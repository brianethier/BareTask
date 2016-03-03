package ca.barelabs.baretask;

import android.annotation.TargetApi;
import android.app.FragmentManager;
import android.os.Handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;

public final class TaskManager implements ActivityTask.OnTaskUpdatedListener {

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

    public interface OnTaskKilledListener {
        void onTaskKilled(int id);
    }

    public interface TaskCallbacks<Progress, Result> {
        void onTaskProgress(ActivityTask task, Progress... progress);
        void onTaskFinished(ActivityTask task, Result result);
    }

    public static abstract class TaskCallbacksAdapter<Progress, Result> implements TaskCallbacks<Progress, Result> {
        public void onTaskProgress(ActivityTask task, Progress... progress) {}
    }

    private static final String TAG = TaskManager.class.getName() + "HSF48EHB732HR75HR63HE8";
    private static final AtomicLong NEXT_ID = new AtomicLong();

    private final Map<Integer, TaskCallbacks> mRegisteredCallbacks = new HashMap<Integer, TaskCallbacks>();
    private final Map<Long, ActivityTask> mManagedTasks = new TreeMap<Long, ActivityTask>();
    private final List<Integer> mKilledTaskIds = new ArrayList<Integer>();
    private final Handler mHandler = new Handler();
    private OnTaskKilledListener mListener;
    private boolean mResumed;


    @Override
    public void onTaskUpdated(final long taskId) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                ActivityTask task = mManagedTasks.get(taskId);
                if (task != null && deliverResult(task)) {
                    task.unregisterListener();
                    mManagedTasks.remove(taskId);
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

    public void registerCallbacks(int id, TaskCallbacks<?,?> callbacks) {
        if (mRegisteredCallbacks.containsKey(id)) {
            throw new IllegalStateException("You can only call registerCallbacks(...) once per id.");
        }
        mRegisteredCallbacks.put(id, callbacks);
    }

    public void unregisterCallbacks(int id) {
        if(mRegisteredCallbacks.containsKey(id)) {
            mRegisteredCallbacks.remove(id);
        }
    }

    public void unregisterAllCallbacks() {
        mRegisteredCallbacks.clear();
    }

    public boolean isResultPending(int id) {
        for (Map.Entry<Long, ActivityTask> entry : mManagedTasks.entrySet()) {
            ActivityTask task = entry.getValue();
            if (task.getId() == id && task.isPendingResult()) {
                return true;
            }
        }
        return false;
    }

    public void processResult(int id, ActivityTask<?,?,?> task) {
        if (!task.isStarted()) {
            task.execute();
        }
        task.setId(id);
        if (!deliverResult(task)) {
            long taskId = NEXT_ID.getAndIncrement();
            task.registerListener(taskId, this);
            mManagedTasks.put(taskId, task);
        }
    }

    public void cancelResult(int id) {
        ActivityTask task = mManagedTasks.get(id);
        if (task != null) {
            task.unregisterListener();
            mManagedTasks.remove(id);
        }
    }

    public void cancelAllResults() {
        for (Map.Entry<Long, ActivityTask> entry : mManagedTasks.entrySet()) {
            ActivityTask task = entry.getValue();
            task.unregisterListener();
        }
        mManagedTasks.clear();
    }

    ArrayList<Integer> getTaskIds() {
        ArrayList<Integer> ids = new ArrayList<Integer>();
        for (Map.Entry<Long, ActivityTask> entry : mManagedTasks.entrySet()) {
            ids.add(entry.getValue().getId());
        }
        return ids;
    }

    void setKilledTaskIds(List<Integer> ids) {
        mKilledTaskIds.clear();
        mKilledTaskIds.addAll(ids);
    }

    void onResume() {
        mResumed = true;
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                deliverKilledTaskIds();
                Iterator<Map.Entry<Long, ActivityTask>> iterator = mManagedTasks.entrySet().iterator();
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
        for (Map.Entry<Long, ActivityTask> entry : mManagedTasks.entrySet()) {
            ActivityTask task = entry.getValue();
            task.unregisterListener();
            task.cancel(false);
        }
        mManagedTasks.clear();
    }

    void onDetach() {
        // Clear all callbacks when the fragment is detached from it's activity!
        mRegisteredCallbacks.clear();
    }

    private void deliverKilledTaskIds() {
        if(mResumed) {
            if (mListener != null) {
                for (Integer id : mKilledTaskIds) {
                    mListener.onTaskKilled(id);
                }
            }
            mKilledTaskIds.clear();
        }
    }

    private boolean deliverResult(ActivityTask task) {
        if (!mResumed) {
            return false;
        }
        if (task.isPendingResult()) {
            TaskCallbacks callbacks = mRegisteredCallbacks.get(task.getId());
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
