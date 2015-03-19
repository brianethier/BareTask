package com.barenode.baretask;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;


public abstract class ActivityTask<PARAMS, PROGRESS, RESULT> {

    public interface OnTaskCompleteListener<PROGRESS, RETURN> {
        public void onTaskComplete(RETURN value);
        public void onTaskProgress(PROGRESS... progress);
        public void onTaskCancelled(RETURN value);
    }

    private final Context mContext;
    private final InternalAsyncTask mTask;
    private OnTaskCompleteListener<PROGRESS, RESULT> mListener;
    private int mId;


    public ActivityTask(Context context) {
        mContext = context.getApplicationContext();
        mTask = new InternalAsyncTask();
    }


    public abstract RESULT doInBackground(PARAMS param);

    public abstract void onCancelled(RESULT value);


    public Context getContext() {
        return mContext;
    }

    public int getId() {
        return mId;
    }

    public boolean isRunning() {
        return mTask.getStatus() == AsyncTask.Status.RUNNING;
    }

    public boolean isCancelled() {
        return mTask.isCancelled();
    }

    protected void publishProgress(PROGRESS progress) {
        mTask.publishInternalProgress(progress);
    }

    protected void cancel(boolean mayInterruptIfRunning) {
        mTask.cancel(mayInterruptIfRunning);
    }

    void start(int id, OnTaskCompleteListener<PROGRESS, RESULT> listener, PARAMS params) {
        if(mTask.getStatus() != AsyncTask.Status.PENDING) {
            throw new IllegalStateException("Start on an ActivityTask can only be called once!");
        }
        mId = id;
        mListener = listener;
        if(Build.VERSION.SDK_INT >= 11) {
            mTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);
        }
        else {
            mTask.execute(params);
        }
    }

    private void onTaskComplete(RESULT result) {
        mListener.onTaskComplete(result);
        mListener = null;
    }

    private void onTaskProgress(PROGRESS... values) {
        if(values != null) {
            mListener.onTaskProgress(values);
        }
    }

    private void onTaskCancelled(RESULT value) {
        mListener.onTaskCancelled(value);
        mListener = null;
        onCancelled(value);
    }



    private class InternalAsyncTask extends AsyncTask<PARAMS, PROGRESS, RESULT> {
        @Override
        protected RESULT doInBackground(PARAMS... params) {
            PARAMS param = params != null && params.length > 0 ? params[0] : null;
            return ActivityTask.this.doInBackground(param);
        }
        @Override
        protected void onProgressUpdate(PROGRESS... values) {
            ActivityTask.this.onTaskProgress(values);
        }
        @Override
        protected void onPostExecute(RESULT value) {
            ActivityTask.this.onTaskComplete(value);
        }
        @Override
        protected void onCancelled(RESULT value) {
            ActivityTask.this.onTaskCancelled(value);
        }

        protected void publishInternalProgress(PROGRESS value) {
            publishProgress(value);
        }
    }
}