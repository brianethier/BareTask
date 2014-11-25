package com.barenode.baretask.rest;

import android.content.Context;

import com.barenode.bareconnection.RestConnection;
import com.barenode.bareconnection.RestException;
import com.barenode.bareconnection.RestProperties;
import com.barenode.baretask.ActivityTask;


public class HttpPutTask extends ActivityTask<Void, HttpResponse> {

    private final RestConnection.Builder mBuilder;
    private Object mValue;


    public HttpPutTask(Context context, String url, Object value) {
        this(context, new RestConnection.Builder().url(url), value);
    }

    public HttpPutTask(Context context, RestProperties properties, Object value) {
        this(context, new RestConnection.Builder().properties(properties), value);
    }

    public HttpPutTask(Context context, RestConnection.Builder builder, Object value) {
        super(context);
        mBuilder = builder;
        mValue = value;
    }

    @Override
    public HttpResponse doInBackground() {
        HttpResponse response = new HttpResponse();
        try {
            RestConnection connection = mBuilder.build();
            String responseValue = connection.put(String.class, mValue);
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
