package ca.barelabs.baretask;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Handler;
import android.support.v4.app.FragmentActivity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

public final class TaskManager implements ActivityTask.OnTaskUpdatedListener {

    @TargetApi(11)
    public static TaskManager getInstance(Activity activity) {
        return getInstance(activity.getFragmentManager());
    }

    public static TaskManager getInstance(FragmentActivity activity) {
        return getInstance(activity.getSupportFragmentManager());
    }

    @TargetApi(11)
    public static TaskManager getInstance(Fragment fragment) {
        return getInstance(fragment.getFragmentManager());
    }

    public static TaskManager getInstance(android.support.v4.app.Fragment fragment) {
        return getInstance(fragment.getFragmentManager());
    }

    @TargetApi(11)
    private static TaskManager getInstance(FragmentManager fm) {
        RetainedNativeFragment fragment = (RetainedNativeFragment) fm.findFragmentByTag(TAG);
        if (fragment == null) {
            fragment = new RetainedNativeFragment();
            fm.beginTransaction().add(fragment, TAG).commit();
        }
        return fragment.getTaskManager();
    }

    private static TaskManager getInstance(android.support.v4.app.FragmentManager fm) {
        RetainedSupportFragment fragment = (RetainedSupportFragment) fm.findFragmentByTag(TAG);
        if (fragment == null) {
            fragment = new RetainedSupportFragment();
            fm.beginTransaction().add(fragment, TAG).commit();
        }
        return fragment.getTaskManager();
    }

    public interface OnTaskKilledListener {
        void onTaskKilled(long id);
    }

    @SuppressWarnings("unchecked")
    public interface TaskCallbacks<Progress, Result> {
        void onTaskProgress(ActivityTask task, Progress... progress);
        void onTaskFinished(ActivityTask task, Result result);
    }

    @SuppressWarnings("unchecked")
    public static abstract class TaskCallbacksAdapter<Progress, Result> implements TaskCallbacks<Progress, Result> {
        public void onTaskProgress(ActivityTask task, Progress... progress) {}
    }

    private static final String TAG = TaskManager.class.getName() + "HSF48EHB732HR75HR63HE8";
    private static final AtomicLong NEXT_ID = new AtomicLong();

    private final Handler mHandler = new Handler();
    private final Map<Long, TaskCallbacks> mCallbacksMap = new HashMap<>();
    private final Map<Long, ActivityTask> mTasksMap = new TreeMap<>();
    private long[] mKilledTaskIds;
    private OnTaskKilledListener mListener;
    private boolean mResumed;


    @Override
    public void onTaskUpdated(final long taskId) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                ActivityTask task = mTasksMap.get(taskId);
                if (task != null && deliverResult(task)) {
                    task.unregisterListener();
                    mTasksMap.remove(taskId);
                }
            }
        });
    }

    public void registerTaskKilledListener(OnTaskKilledListener listener) {
        mListener = listener;
    }

    public void unregisterTaskKilledListener() {
        mListener = null;
    }

    public void registerCallbacks(long callbackId, TaskCallbacks<?, ?> callbacks) {
        if (mCallbacksMap.containsKey(callbackId)) {
            throw new IllegalStateException("You can only call registerCallbacks(...) once per id.");
        }
        mCallbacksMap.put(callbackId, callbacks);
    }

    public void unregisterCallbacks(long callbackId) {
        if (mCallbacksMap.containsKey(callbackId)) {
            mCallbacksMap.remove(callbackId);
        }
    }

    public void unregisterAllCallbacks() {
        mCallbacksMap.clear();
    }

    public boolean isResultPending(long callbackId) {
        ActivityTask task = findTask(callbackId);
        return task != null && task.isPendingResult();
    }

    public void processResult(long callbackId, ActivityTask<?, ?, ?> task) {
        if (!task.isStarted()) {
            throw new IllegalStateException("You must call execute() on your task before processing result.");
        }
        task.setCallbackId(callbackId);
        if (!deliverResult(task)) {
            long taskId = NEXT_ID.getAndIncrement();
            task.registerListener(taskId, this);
            mTasksMap.put(taskId, task);
        }
    }

    public void cancelResult(long callbackId) {
        ActivityTask task = findTask(callbackId);
        if (task != null) {
            task.unregisterListener();
            mTasksMap.remove(task.getTaskId());
        }
    }

    public void cancelAllResults() {
        for (Map.Entry<Long, ActivityTask> entry : mTasksMap.entrySet()) {
            ActivityTask task = entry.getValue();
            task.unregisterListener();
        }
        mTasksMap.clear();
    }

    long[] getTaskIds() {
        long[] taskIds = new long[mTasksMap.size()];
        int i = 0;
        for (Long taskId : mTasksMap.keySet()) {
            taskIds[i++] = taskId;
        }
        return taskIds;
    }

    void setKilledTaskIds(long[] taskIds) {
        mKilledTaskIds = taskIds;
    }

    void onResume() {
        mResumed = true;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                deliverKilledTaskIds();
                Iterator<Map.Entry<Long, ActivityTask>> iterator = mTasksMap.entrySet().iterator();
                while (iterator.hasNext()) {
                    if (deliverResult(iterator.next().getValue())) {
                        iterator.remove();
                    }
                }
            }
        });
    }

    void onPause() {
        mResumed = false;
    }

    void onDestroy() {
        // User hit the back button or called finish(), or OS cleaned up activity to reclaim resources
        // Just cancel the workers so that the onCancelled(...) is triggered
        for (Map.Entry<Long, ActivityTask> entry : mTasksMap.entrySet()) {
            ActivityTask task = entry.getValue();
            task.unregisterListener();
            task.cancel(false);
        }
        mTasksMap.clear();
    }

    void onDetach() {
        // Clear all callbacks when the fragment is detached from it's activity!
        mCallbacksMap.clear();
    }

    private ActivityTask findTask(long callbackId) {
        for (Map.Entry<Long, ActivityTask> entry : mTasksMap.entrySet()) {
            ActivityTask task = entry.getValue();
            if (task.getCallbackId() == callbackId && task.isPendingResult()) {
                return task;
            }
        }
        return null;
    }

    private void deliverKilledTaskIds() {
        if(mResumed) {
            if (mListener != null && mKilledTaskIds != null) {
                for (Long id : mKilledTaskIds) {
                    mListener.onTaskKilled(id);
                }
            }
            mKilledTaskIds = null;
        }
    }

    @SuppressWarnings("unchecked")
    private boolean deliverResult(ActivityTask task) {
        if (!mResumed) {
            return false;
        }
        if (task.isPendingResult()) {
            TaskCallbacks callbacks = mCallbacksMap.get(task.getCallbackId());
            if (callbacks != null) {
                if (task.isResultCompleted()) {
                    task.setResultDelivered();
                    callbacks.onTaskFinished(task, task.getResult());
                    return true;
                } else if (task.isProgressPublished()) {
                    callbacks.onTaskProgress(task, task.getProgress());
                }
            }
            return false;
        } else {
            // Task was cancelled or previously delivered
            return true;
        }
    }
}
