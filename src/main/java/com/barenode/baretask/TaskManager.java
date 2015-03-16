package com.barenode.baretask;

import android.annotation.TargetApi;
import android.app.FragmentManager;
import android.os.Handler;
import android.util.SparseArray;

import java.util.ArrayList;


public final class TaskManager {

    private static final String TAG = TaskManager.class.getName() + "HSF48EHB732HR75HR63HE8";

    private enum TaskState {
        IDLE,
        RUNNING,
        COMPLETE,
        KILLED
    }

    @TargetApi(11)
    public static final TaskManager findOrCreate(FragmentManager fm) {
        RetainedNativeFragment fragment = (RetainedNativeFragment) fm.findFragmentByTag(TAG);
        if(fragment == null) {
            fragment = new RetainedNativeFragment();
            fm.beginTransaction().add(fragment, TAG).commit();
        }
        return fragment.getTaskManager();
    }

    public static final TaskManager findOrCreate(android.support.v4.app.FragmentManager fm) {
        RetainedSupportFragment fragment = (RetainedSupportFragment) fm.findFragmentByTag(TAG);
        if(fragment == null) {
            fragment = new RetainedSupportFragment();
            fm.beginTransaction().add(fragment, TAG).commit();
        }
        return fragment.getTaskManager();
    }


    public interface TaskCallbacks<PROGRESS, RESULT> {
        public ActivityTask<PROGRESS, RESULT> onCreateTask(int id);
        public void onTaskFinished(int id, RESULT result);
        public void onTaskProgress(int id, PROGRESS... progress);
        public void onTaskKilled(int id);
    }

    public static abstract class TaskCallbacksAdapter<PROGRESS, RESULT> implements TaskCallbacks<PROGRESS, RESULT> {
        public void onTaskProgress(int id, PROGRESS... progress) {}
        public void onTaskKilled(int id) {}
    }


    private final SparseArray<ActivityTaskInfo> mTaskInfos = new SparseArray<ActivityTaskInfo>(0);
    private final Handler mHandler = new Handler();
    private final Runnable mActiveTaskRunner = new Runnable() {
        @Override
        public void run() {
            // This is run once the fragment is started and all the registered Workers become active.
            // So attempt delivery for any workers that finished when the activity was stopped or killed
            recreateKilledTasks();
            for (int i = mTaskInfos.size()-1; i >= 0; i--) {
                ActivityTaskInfo info = mTaskInfos.valueAt(i);
                if(info.isWaitingForResult()) {
                    info.attemptDelivery();
                }
            }
        }
    };
    private boolean mActive;
    private ArrayList<Integer> mKilledTaskIds;


    public ArrayList<Integer> getTaskIds() {
        ArrayList<Integer> workerIds = new ArrayList<Integer>();
        for (int i = mTaskInfos.size()-1; i >= 0; i--) {
            ActivityTaskInfo info = mTaskInfos.valueAt(i);
            if(info.isWaitingForResult()) {
                workerIds.add(info.getId());
            }
        }
        return workerIds;
    }

    public boolean isTaskRunning(int id) {
        ActivityTaskInfo info = mTaskInfos.get(id);
        return info == null ? false : info.isRunning();
    }

    public void registerCallbacks(int id, TaskCallbacks<?,?> callbacks) {
        ActivityTaskInfo info = mTaskInfos.get(id);
        if(info == null) {
            info = new ActivityTaskInfo(id);
            info.setActive(mActive);
            mTaskInfos.put(id, info);
        }
        info.setCallbacks((TaskCallbacks<Object, Object>) callbacks);
    }

    public void unregisterCallbacks(int id) {
        ActivityTaskInfo info = mTaskInfos.get(id);
        if(info != null) {
            info.setCallbacks(null);
        }
    }

    public void unregisterAllCallbacks() {
        for (int i = mTaskInfos.size()-1; i >= 0; i--) {
            mTaskInfos.valueAt(i).setCallbacks(null);
        }
    }

    public void startTask(int id) {
        ActivityTaskInfo info = mTaskInfos.get(id);
        if(info == null) {
            throw new IllegalStateException("Your must call registerCallbacks(...) before you can start a task!");
        }
        info.createAndStart();
    }

    public void cancelTask(int id) {
        ActivityTaskInfo info = mTaskInfos.get(id);
        if(info == null) {
            throw new IllegalStateException("Your must call registerCallbacks(...) before you can start/cancel a task!");
        }
        info.cancel();
    }

    public void cancelAllTask() {
        for(int i = mTaskInfos.size()-1; i >= 0; i--) {
            mTaskInfos.valueAt(i).cancel();
        }
    }

    void setTasksActive(boolean active) {
        mActive = active;
        for(int i = mTaskInfos.size()-1; i >= 0; i--) {
            mTaskInfos.valueAt(i).setActive(active);
        }
        if(active) {
            mHandler.post(mActiveTaskRunner);
        }
    }

    void setKilledTaskIds(ArrayList<Integer> killedTaskIds) {
        mKilledTaskIds = killedTaskIds;
    }

    private void recreateKilledTasks() {
        if(mKilledTaskIds != null) {
            for(Integer id : mKilledTaskIds) {
                ActivityTaskInfo info = mTaskInfos.get(id);
                if(info != null) {
                    info.createAndSetKilled();
                }
            }
            mKilledTaskIds = null;
        }
    }



    private static class ActivityTaskInfo implements ActivityTask.OnTaskCompleteListener<Object, Object> {

        private final int mId;
        private ActivityTask<Object, Object> mActivityTask;
        private TaskManager.TaskCallbacks<Object, Object> mCallbacks;
        private Object mResult;
        private Object[] mProgress;
        private boolean mProgressPublished;
        private boolean mActive;
        private TaskState mState = TaskState.IDLE;


        private ActivityTaskInfo(int id) {
            mId = id;
        }


        private int getId() {
            return mId;
        }

        private boolean isRunning() {
            return mActivityTask != null && mActivityTask.isRunning();
        }

        private boolean isWaitingForResult() {
            return mState != TaskState.IDLE;
        }

        private void setCallbacks(TaskManager.TaskCallbacks<Object, Object> callbacks) {
            mCallbacks = callbacks;
        }

        private void setActive(boolean active) {
            mActive = active;
            deliverProgress();
        }

        private void attemptDelivery() {
            deliverResult();
        }

        private void createAndSetKilled() {
            if(mCallbacks == null) {
                throw new IllegalStateException("Make sure this worker has been registered!");
            }
            mState = TaskState.KILLED;
            mActivityTask = mCallbacks.onCreateTask(mId);
        }

        private void createAndStart() {
            if(mCallbacks == null) {
                throw new IllegalStateException("Make sure this worker has been registered!");
            }
            if(isWaitingForResult()) {
                throw new IllegalStateException("You can't start the same worker until it has completed its work!");
            }
            mState = TaskState.RUNNING;
            mActivityTask = mCallbacks.onCreateTask(mId);
            mActivityTask.start(mId, this);
        }

        private void cancel() {
            if(mActivityTask != null) {
                // Just cancel the worker here, onTaskCancelled() will be called
                mActivityTask.cancel(false);
            }
        }

        @Override
        public void onTaskComplete(Object result) {
            mResult = result;
            mState = TaskState.COMPLETE;
            deliverResult();
        }

        @Override
        public void onTaskProgress(Object... progress) {
            mProgress = progress;
            mProgressPublished = true;
            deliverProgress();
        }

        @Override
        public void onTaskCancelled(Object result) {
            resetState();
        }

        private void deliverResult() {
            if(mActive && mCallbacks != null && (mState == TaskState.COMPLETE || mState == TaskState.KILLED)) {
                // Reset state before delivering results so that within callbacks, isTaskRunning will return false
                Object result = mResult;
                TaskState state = mState;
                resetState();
                if(state == TaskState.COMPLETE) {
                    mCallbacks.onTaskFinished(mId, result);
                }
                else {
                    mCallbacks.onTaskKilled(mId);
                }
            }
        }

        private void deliverProgress() {
            if(mActive && mProgressPublished && mCallbacks != null && mState == TaskState.RUNNING) {
                mCallbacks.onTaskProgress(mId, mProgress);
            }
        }

        private void resetState() {
            mActivityTask = null;
            mResult = null;
            mProgress = null;
            mProgressPublished = false;
            mState = TaskState.IDLE;
        }
    }
}
