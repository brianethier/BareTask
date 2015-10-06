package com.barenode.baretask;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Build;

public abstract class ActivityTask<Params, Progress, Result> {

    interface OnTaskListener<Progress, Result> {
        void onTaskProgress(ActivityTask task, Progress... progress);
        void onTaskComplete(ActivityTask task, Result result);
        void onTaskCancelled(ActivityTask task);
    }

    private final Context mContext;
    private final InternalAsyncTask mInternalTask;
    private int mId;
    private OnTaskListener<Progress, Result> mTaskListener;
    private Progress[] mProgress;
    private Result mResult;
    private Exception mException;
    private boolean mProgressPublished;
    private boolean mCompleted;
    private boolean mCancelled;

    public ActivityTask(Context context) {
        mContext = context.getApplicationContext();
        mInternalTask = new InternalAsyncTask();
    }

    public Context getContext() {
        return mContext;
    }

    public int getId() {
        return mId;
    }

    public boolean isSuccessful() {
        return mCompleted && mException == null;
    }

    public Exception getException() {
        return mException;
    }

    public void execute(Params... params) {
        if(mInternalTask.getStatus() != AsyncTask.Status.PENDING) {
            throw new IllegalStateException("An ActivityTask can only be executed once!");
        }
        if(Build.VERSION.SDK_INT >= 11) {
            mInternalTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, params);
        }
        else {
            mInternalTask.execute(params);
        }
    }

    public abstract Result doInBackground(Params... params) throws Exception;

    public abstract void onCancelled(Result value);

    protected boolean isCancelled() {
        return mInternalTask.isCancelled();
    }

    protected void publishProgress(Progress... progress) {
        mInternalTask.publishInternalProgress(progress);
    }

    protected void cancel(boolean mayInterruptIfRunning) {
        mInternalTask.cancel(mayInterruptIfRunning);
    }

    void setId(int id) {
        mId = id;
    }

    void registerListener(OnTaskListener<Progress, Result> taskListener) {
        mTaskListener = taskListener;
    }

    boolean isRunning() {
        return mInternalTask.getStatus() == AsyncTask.Status.RUNNING;
    }

    void deliverResults() {
        if(mTaskListener != null) {
            if (mCancelled) {
                mTaskListener.onTaskCancelled(this);
            } else if (mCompleted) {
                mTaskListener.onTaskComplete(this, mResult);
            } else if (mProgressPublished) {
                mTaskListener.onTaskProgress(this, mProgress);
            }
        }
    }

    private void onTaskProgress(Progress... values) {
        mProgressPublished = true;
        mProgress = values;
        deliverResults();
    }

    private void onTaskComplete(Result result) {
        mCompleted = true;
        mResult = result;
        deliverResults();
    }

    private void onTaskException(Exception exception) {
        mCompleted = true;
        mException = exception;
        deliverResults();
    }

    private void onTaskCancelled(Result result) {
        mCancelled = true;
        onCancelled(result);
        deliverResults();
    }


    private class InternalAsyncTask extends AsyncTask<Params, Progress, Result> {

        private Exception mException;

        @Override
        protected Result doInBackground(Params... params) {
            try {
                return ActivityTask.this.doInBackground(params);
            } catch(Exception e) {
                mException = e;
                return null;
            }
        }

        @Override
        protected void onProgressUpdate(Progress... values) {
            ActivityTask.this.onTaskProgress(values);
        }

        @Override
        protected void onPostExecute(Result value) {
            if(mException == null) {
                ActivityTask.this.onTaskComplete(value);
            } else {
                ActivityTask.this.onTaskException(mException);
            }
        }

        @Override
        protected void onCancelled(Result value) {
            ActivityTask.this.onTaskCancelled(value);
        }

        protected void publishInternalProgress(Progress... value) {
            publishProgress(value);
        }
    }
}