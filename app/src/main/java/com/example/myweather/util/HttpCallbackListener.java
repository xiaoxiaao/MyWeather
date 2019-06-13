package com.example.myweather.util;

public interface HttpCallbackListener {

    void onFinish(String response);

    void onError(Exception e);

}
