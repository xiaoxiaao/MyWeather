package com.example.myweather.activity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.myweather.R;
import com.example.myweather.db.CityDB;
import com.example.myweather.model.City;
import com.example.myweather.model.County;
import com.example.myweather.model.Province;
import com.example.myweather.util.HttpCallbackListener;
import com.example.myweather.util.HttpUtil;
import com.example.myweather.util.Utility;

import java.util.ArrayList;
import java.util.List;

public class ChooseAreaActivity  extends Activity {
    public static final int LEVEL_PROVINCE = 0;
    public static final int LEVEL_CITY = 1;
    public static final int LEVEL_COUNTY = 2;

    private ProgressDialog progressDialog;
    private TextView titleText;
    private ListView listView;
    private ArrayAdapter<String> adapter;
    private CityDB cityDB;
    private List<String> dataList = new ArrayList<String>();
    //省列表
    private List<Province> provinceList;

     //市列表
    private List<City> cityList;

     //县列表
    private List<County> countyList;

     //选中的省份
    private Province selectedProvince;

     // 选中的城市
    private City selectedCity;

    //当前选中的级别
    private int currentLevel;

     // 是否从WeatherActivity中跳转过来。
    private boolean isFromWeatherActivity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        isFromWeatherActivity = getIntent().getBooleanExtra("from_weather_activity", false);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (prefs.getBoolean("city_selected", false) && !isFromWeatherActivity) {
            Intent intent = new Intent(this, WeatherActivity.class);
            startActivity(intent);
            finish();
            return;
        }


        //初始化控件
        requestWindowFeature(Window.FEATURE_NO_TITLE);//隐藏标题
        setContentView(R.layout.choose_area);
        listView = (ListView) findViewById(R.id.list_view);
        titleText = (TextView) findViewById(R.id.title_text);
        //初始化Arraydapter
        adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, dataList);
        //设置ListView的适配器
        listView.setAdapter(adapter);
        //获得CityDB的实例
        cityDB = CityDB.getInstance(this);

        //列表点击事件监听器
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View view, int index,
                                    long arg3) {
                if (currentLevel == LEVEL_PROVINCE) {
                    selectedProvince = provinceList.get(index);
                    queryCities();
                } else if (currentLevel == LEVEL_CITY) {
                    selectedCity = cityList.get(index);
                    queryCounties();
                } else if (currentLevel == LEVEL_COUNTY) {
                    String countyCode = countyList.get(index).getCountyCode();
                    Intent intent = new Intent(ChooseAreaActivity.this, WeatherActivity.class);
                    intent.putExtra("county_code", countyCode);
                    startActivity(intent);
                    finish();
                }
            }
        });

        // 加载省级数据
        queryProvinces();
    }


     // 查询全国所有的省，并列出表单
     // 优先从数据库查询，如果没有查询到再去服务器上查询。
    private void queryProvinces() {
        provinceList = cityDB.loadProvinces();
        if (provinceList.size() > 0) {
            dataList.clear();
            for (Province province : provinceList) {
                dataList.add(province.getProvinceName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            titleText.setText("中国");
            currentLevel = LEVEL_PROVINCE;
        } else {
            queryFromServer(null, "province");
        }
    }


     // 查询选中省内所有的市
     // 优先从数据库查询，如果没有查询到再去服务器上查询
    private void queryCities() {
        cityList = cityDB.loadCities(selectedProvince.getId());
        if (cityList.size() > 0) {//在数据库中查询

            dataList.clear();//清空dataList列表
            for (City city : cityList) {
                //将city表中的城市名加入dataList中
                dataList.add(city.getCityName());
            }

            adapter.notifyDataSetChanged();//重绘listView
            listView.setSelection(0);//将列表移动到第0号位置
            titleText.setText(selectedProvince.getProvinceName());//显示列表

            currentLevel = LEVEL_CITY;//调整列表到city级
        } else {
            //在服务器中查询
            queryFromServer(selectedProvince.getProvinceCode(), "city");
        }
    }


     // 查询选中市内所有的县，优先从数据库查询，如果没有查询到再去服务器上查询。
    private void queryCounties() {
        countyList = cityDB.loadCounties(selectedCity.getId());
        if (countyList.size() > 0) {
            dataList.clear();
            for (County county : countyList) {
                dataList.add(county.getCountyName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            titleText.setText(selectedCity.getCityName());
            currentLevel = LEVEL_COUNTY;
        } else {
            queryFromServer(selectedCity.getCityCode(), "county");
        }
    }


     // 根据传入的代号和类型从服务器上查询省市县数据。
    /*
    根据传入参数拼装查询地址，
    调用HttpUtil中的sendHttpRequest（）方法向服务器发出请求，
    请求响应的数据回调到onFinish（）方法中，
    然后调用Utility的handleProvincesResponse（）方法解析返回的数据，并存储到数据库中
    再次调用queryProvinces（）重新加载
     */
    private void queryFromServer(final String code, final String type) {
        String address;
        if (!TextUtils.isEmpty(code)) {
            //使用code进行查询
            address = "http://www.weather.com.cn/data/list3/city" + code + ".xml";
        } else {
            address = "http://www.weather.com.cn/data/list3/city.xml";
        }
        showProgressDialog();//显示进度对话框

        //创建网络连接
        HttpUtil.sendHttpRequest(address, new HttpCallbackListener() {

            //当HttpCallbackListener==null,回调onFinish方法
            //解析获取到的JSON数据
            @Override
            public void onFinish(String response) {
                boolean result = false;
                if ("province".equals(type)) {
                    result = Utility.handleProvincesResponse(cityDB,
                            response);
                } else if ("city".equals(type)) {
                    result = Utility.handleCitiesResponse(cityDB,
                            response, selectedProvince.getId());
                } else if ("county".equals(type)) {
                    result = Utility.handleCountiesResponse(cityDB,
                            response, selectedCity.getId());
                }
                if (result) {
                    // 通过runOnUiThread()方法回到主线程处理逻辑
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            closeProgressDialog();
                            if ("province".equals(type)) {
                                queryProvinces();
                            } else if ("city".equals(type)) {
                                queryCities();
                            } else if ("county".equals(type)) {
                                queryCounties();
                            }
                        }
                    });
                }
            }

            //回调报错
            @Override
            public void onError(Exception e) {
                // 通过runOnUiThread()方法回到主线程处理逻辑
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        closeProgressDialog();
                        Toast.makeText(ChooseAreaActivity.this,
                                "加载失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }


     // 显示进度对话框
    private void showProgressDialog() {
        if (progressDialog == null) {
            progressDialog = new ProgressDialog(this);
            progressDialog.setMessage("正在加载...");
            progressDialog.setCanceledOnTouchOutside(false);//点击屏幕其他地方，对话框不会消失
        }
        progressDialog.show();
    }


     //关闭进度对话框
    private void closeProgressDialog() {
        if (progressDialog != null) {
            progressDialog.dismiss();
        }
    }

    //捕获Back按键，根据当前的级别来判断，此时应该返回市列表、省列表、还是直接退出
    @Override
    public void onBackPressed() {
        if (currentLevel == LEVEL_COUNTY) {
            queryCities();
        } else if (currentLevel == LEVEL_CITY) {
            queryProvinces();
        } else {
            if (isFromWeatherActivity) {
                Intent intent = new Intent(this, WeatherActivity.class);
                startActivity(intent);
            }
            finish();
        }
    }

}
