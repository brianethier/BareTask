package com.barenode.baretask.rest;

import android.content.Context;

import com.barenode.bareconnection.RestConnection;
import com.barenode.bareconnection.RestException;
import com.barenode.bareconnection.RestProperties;
import com.barenode.baretask.ActivityTask;


public class HttpPutTask extends ActivityTask<Object, Void, HttpResponse> {

    private final RestConnection.Builder mBuilder;


    public HttpPutTask(Context context, String url) {
        this(context, new RestConnection.Builder().url(url));
    }

    public HttpPutTask(Context context, RestProperties properties) {
        this(context, new RestConnection.Builder().properties(properties));
    }

    public HttpPutTask(Context context, RestConnection.Builder builder) {
        super(context);
        mBuilder = builder;
    }

    @Override
    public HttpResponse doInBackground(Object value) {
        HttpResponse response = new HttpResponse();
        try {
            RestConnection connection = mBuilder.build();
            String responseValue = connection.put(String.class, value);
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
