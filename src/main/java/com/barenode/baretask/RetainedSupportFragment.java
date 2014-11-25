package com.barenode.baretask;

import android.os.Bundle;
import android.support.v4.app.Fragment;


public class RetainedSupportFragment extends Fragment {

    public static final String KILLED_TASK_IDS_KEY = "KILLED_TASK_IDS_KEY";


    private final TaskManager mManager = new TaskManager();


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        // If this is being called with a Bundle then the activity was killed by the OS and re-created
        if(savedInstanceState != null) {
            mManager.setKilledTaskIds(savedInstanceState.getIntegerArrayList(KILLED_TASK_IDS_KEY));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mManager.setTasksActive(true);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putIntegerArrayList(KILLED_TASK_IDS_KEY, mManager.getTaskIds());
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onPause() {
        super.onPause();
        mManager.setTasksActive(false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // User hit the back button or called finish(), or OS cleaned up activity to reclaim resources
        // Just cancel the workers so that the onCancelled(...) is triggered
        mManager.cancelAllTask();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        // Clear all callbacks when the fragment is detached from it's activity!
        mManager.unregisterAllCallbacks();
    }

    public TaskManager getTaskManager() {
        return mManager;
    }
}
