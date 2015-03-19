package com.barenode.baretask.rest;

import android.content.Context;

import com.barenode.bareconnection.RestConnection;
import com.barenode.bareconnection.RestException;
import com.barenode.bareconnection.RestProperties;
import com.barenode.baretask.ActivityTask;


public class HttpDeleteTask extends ActivityTask<Void, Void, HttpResponse> {

    private final RestConnection.Builder mBuilder;


    public HttpDeleteTask(Context context, String url) {
        this(context, new RestConnection.Builder().url(url));
    }

    public HttpDeleteTask(Context context, RestProperties properties) {
        this(context, new RestConnection.Builder().properties(properties));
    }

    public HttpDeleteTask(Context context, RestConnection.Builder builder) {
        super(context);
        mBuilder = builder;
    }

    @Override
    public HttpResponse doInBackground(Void params) {
        HttpResponse response = new HttpResponse();
        try {
            RestConnection connection = mBuilder.build();
            String responseValue = connection.delete(String.class);
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
