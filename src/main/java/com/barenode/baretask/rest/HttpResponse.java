package com.barenode.baretask.rest;

import com.barenode.bareconnection.RestException;
import com.barenode.bareconnection.RestUtils;


public class HttpResponse {

    private String mResponse;
    private RestException mException;


    public void setResponse(String response) {
        mResponse = response;
    }

    public String getResponse() {
        return mResponse;
    }

    public <T> T getResponse(Class<T> clss) {
        return RestUtils.fromJson(mResponse, clss);
    }

    public void setException(RestException exception) {
        mException = exception;
    }

    public RestException getException() {
        return mException;
    }

    public boolean isSuccessful() {
        return mException == null;
    }
}
