package com.barenode.baretask.rest;

import android.content.Context;

import com.barenode.bareconnection.RestConnection;
import com.barenode.bareconnection.RestException;
import com.barenode.bareconnection.RestProperties;
import com.barenode.baretask.ActivityTask;


public class HttpPostTask extends ActivityTask<Void, HttpResponse> {

    private final RestConnection.Builder mBuilder;
    private Object mValue;


    public HttpPostTask(Context context, String url, Object value) {
        this(context, new RestConnection.Builder().url(url), value);
    }

    public HttpPostTask(Context context, RestProperties properties, Object value) {
        this(context, new RestConnection.Builder().properties(properties), value);
    }

    public HttpPostTask(Context context, RestConnection.Builder builder, Object value) {
        super(context);
        mBuilder = builder;
        mValue = value;
    }

    @Override
    public HttpResponse doInBackground() {
        HttpResponse response = new HttpResponse();
        try {
            RestConnection connection = mBuilder.build();
            String responseValue = connection.post(String.class, mValue);
            response.setResponse(responseValue);
        } catch (RestException e) {
            response.setException(e);
        }
        return response;
    }

    @Override
    public void onCancelled(HttpResponse response) {
        // Nothing to do
    }
}
