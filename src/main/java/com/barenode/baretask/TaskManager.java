package com.barenode.baretask;

import android.annotation.TargetApi;
import android.app.FragmentManager;
import android.os.Handler;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.List;

public final class TaskManager implements ActivityTask.OnTaskListener {

    private static final String TAG = TaskManager.class.getName() + "HSF48EHB732HR75HR63HE8";

    @TargetApi(11)
    public static TaskManager findOrCreate(FragmentManager fm) {
        RetainedNativeFragment fragment = (RetainedNativeFragment) fm.findFragmentByTag(TAG);
        if(fragment == null) {
            fragment = new RetainedNativeFragment();
            fm.beginTransaction().add(fragment, TAG).commit();
        }
        return fragment.getTaskManager();
    }

    public static TaskManager findOrCreate(android.support.v4.app.FragmentManager fm) {
        RetainedSupportFragment fragment = (RetainedSupportFragment) fm.findFragmentByTag(TAG);
        if(fragment == null) {
            fragment = new RetainedSupportFragment();
            fm.beginTransaction().add(fragment, TAG).commit();
        }
        return fragment.getTaskManager();
    }

    public interface TaskKilledListener {
        void onTaskKilled(int id);
    }

    public interface TaskCallbacks<Progress, Result> {
        void onTaskProgress(ActivityTask task, Progress... progress);
        void onTaskFinished(ActivityTask task, Result result);
    }

    public static abstract class TaskCallbacksAdapter<Progress, Result> implements TaskCallbacks<Progress, Result> {
        public void onTaskProgress(ActivityTask task, Progress... progress) {}
    }

    private final SparseArray<TaskCallbacks> mRegisteredCallbacks = new SparseArray<TaskCallbacks>();
    private final List<ActivityTask> mManagedTasks = new ArrayList<ActivityTask>();
    private final List<Integer> mKilledTaskIds = new ArrayList<Integer>();
    private final Handler mHandler = new Handler();
    private final Runnable mResumeTaskRunner = new Runnable() {
        @Override
        public void run() {
            processKilledTasks();
            List<ActivityTask> managedTasks = new ArrayList<ActivityTask>(mManagedTasks);
            for (ActivityTask task : managedTasks) {
                task.deliverResults();
            }
        }
    };
    private TaskKilledListener mListener;
    private boolean mResumed;


    @Override
    public void onTaskProgress(ActivityTask task, Object[] progress) {
        TaskCallbacks callbacks = mRegisteredCallbacks.get(task.getId());
        if (mResumed && callbacks != null) {
            callbacks.onTaskProgress(task, progress);
        }
    }

    @Override
    public void onTaskComplete(ActivityTask task, Object value) {
        // First make sure we have a registered callback
        TaskCallbacks callbacks = mRegisteredCallbacks.get(task.getId());
        if (mResumed && callbacks != null) {
            callbacks.onTaskFinished(task, value);
            mManagedTasks.remove(task);
        }
    }

    @Override
    public void onTaskCancelled(ActivityTask task) {
        mManagedTasks.remove(task);
    }

    public void registerTaskKilledListener(TaskKilledListener listener) {
        mListener = listener;
    }

    public void unregisterTaskKilledListener() {
        mListener = null;
    }

    public void registerCallbacks(int id, TaskCallbacks<?,?> callbacks) {
        if(mResumed || mRegisteredCallbacks.get(id) != null) {
            throw new IllegalStateException("You can only call registerCallbacks(...) once per id and before onResume().");
        }
        mRegisteredCallbacks.put(id, callbacks);
    }

    public void unregisterCallbacks(int id) {
        if(mRegisteredCallbacks.get(id) != null) {
            mRegisteredCallbacks.remove(id);
        }
    }

    public boolean isTaskRunning(int id) {
        for(ActivityTask task : mManagedTasks) {
            if(task.getId() == id && task.isRunning()) {
                return true;
            }
        }
        return false;
    }

    public void processResult(int id, ActivityTask<?,?,?> task) {
        task.setId(id);
        task.registerListener(this);
        mManagedTasks.add(task);
        task.deliverResults();
    }

    ArrayList<Integer> getTaskIds() {
        ArrayList<Integer> ids = new ArrayList<Integer>();
        for (ActivityTask task : mManagedTasks) {
            ids.add(task.getId());
        }
        return ids;
    }

    void setKilledTaskIds(List<Integer> ids) {
        mKilledTaskIds.clear();
        mKilledTaskIds.addAll(ids);
    }

    void onResume() {
        mResumed = true;
        mHandler.post(mResumeTaskRunner);
    }

    void onPause() {
        mResumed = false;
    }

    void onDestroy() {
        // User hit the back button or called finish(), or OS cleaned up activity to reclaim resources
        // Just cancel the workers so that the onCancelled(...) is triggered
        for (int i = 0; i < mManagedTasks.size(); i++) {
            mManagedTasks.get(i).cancel(false);
        }
        mManagedTasks.clear();
    }

    void onDetach() {
        // Clear all callbacks when the fragment is detached from it's activity!
        mRegisteredCallbacks.clear();
    }

    private void processKilledTasks() {
        if(mResumed && mListener != null) {
            for (Integer id : mKilledTaskIds) {
                mListener.onTaskKilled(id);
            }
        }
        mKilledTaskIds.clear();
    }
}
