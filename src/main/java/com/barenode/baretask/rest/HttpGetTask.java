package com.barenode.baretask.rest;

import android.content.Context;

import com.barenode.bareconnection.RestConnection;
import com.barenode.bareconnection.RestException;
import com.barenode.bareconnection.RestProperties;
import com.barenode.baretask.ActivityTask;


public class HttpGetTask extends ActivityTask<Void, Void, HttpResponse> {

    private final RestConnection.Builder mBuilder;
    private Object mValue;


    public HttpGetTask(Context context, String url) {
        this(context, new RestConnection.Builder().url(url));
    }

    public HttpGetTask(Context context, RestProperties properties) {
        this(context, new RestConnection.Builder().properties(properties));
    }

    public HttpGetTask(Context context, RestConnection.Builder builder) {
        super(context);
        mBuilder = builder;
    }

    @Override
    public HttpResponse doInBackground(Void params) {
        HttpResponse response = new HttpResponse();
        try {
            RestConnection connection = mBuilder.build();
            String responseValue = connection.get(String.class);
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
