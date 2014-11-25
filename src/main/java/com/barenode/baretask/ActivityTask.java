package com.barenode.baretask;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;


public abstract class ActivityTask<PROGRESS, RESULT> {

    public interface OnTaskCompleteListener<PROGRESS, RETURN> {
        public void onTaskComplete(ActivityTask<PROGRESS, RETURN> activityTask, RETURN value);
        public void onTaskProgress(ActivityTask<PROGRESS, RETURN> activityTask, PROGRESS value);
        public void onTaskCancelled(ActivityTask<PROGRESS, RETURN> activityTask, RETURN value);
    }

    private final Context mContext;
    private final InternalAsyncTask mTask;
    private OnTaskCompleteListener<PROGRESS, RESULT> mListener;
    private int mId;


    public ActivityTask(Context context) {
        mContext = context.getApplicationContext();
        mTask = new InternalAsyncTask();
    }


    public abstract RESULT doInBackground();

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

    void start(int id, OnTaskCompleteListener<PROGRESS, RESULT> listener) {
        if(mTask.getStatus() != AsyncTask.Status.PENDING) {
            throw new IllegalStateException("Start on an ActivityTask can only be called once!");
        }
        mId = id;
        mListener = listener;
        if(Build.VERSION.SDK_INT >= 11) {
            mTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
        else {
            mTask.execute();
        }
    }

    private void onTaskComplete(RESULT result) {
        mListener.onTaskComplete(this, result);
        mListener = null;
    }

    private void onTaskProgress(PROGRESS... values) {
        if(values != null && values.length > 0) {
            mListener.onTaskProgress(this, values[0]);
        }
    }

    private void onTaskCancelled(RESULT value) {
        mListener.onTaskCancelled(this, value);
        mListener = null;
        onCancelled(value);
    }



    private class InternalAsyncTask extends AsyncTask<Void, PROGRESS, RESULT> {
        @Override
        protected RESULT doInBackground(Void... voids) {
            return ActivityTask.this.doInBackground();
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