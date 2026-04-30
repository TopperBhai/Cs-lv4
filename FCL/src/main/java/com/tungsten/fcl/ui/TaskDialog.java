package com.tungsten.fcl.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ListView;

import androidx.annotation.NonNull;

import com.tungsten.fcl.R;
import com.tungsten.fcl.util.TaskCancellationAction;
import com.tungsten.fclcore.fakefx.beans.property.StringProperty;
import com.tungsten.fclcore.task.FileDownloadTask;
import com.tungsten.fclcore.task.Schedulers;
import com.tungsten.fclcore.task.TaskExecutor;
import com.tungsten.fclcore.task.TaskListener;
import com.tungsten.fcllibrary.component.dialog.FCLDialog;
import com.tungsten.fcllibrary.component.view.FCLButton;
import com.tungsten.fcllibrary.component.view.FCLTextView;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.Consumer;

public class TaskDialog extends FCLDialog implements View.OnClickListener {

    private FCLTextView titleView;
    private FCLTextView speedView;
    private FCLButton cancelButton;

    private TaskExecutor executor;
    private TaskCancellationAction onCancel;
    private final Consumer<FileDownloadTask.SpeedEvent> speedEventHandler;

    private TaskListPane taskListPane;
    private ListView taskListView;

    @SuppressLint("DefaultLocale")
    public TaskDialog(@NonNull Context context, @NotNull TaskCancellationAction cancel) {
        super(context);
        setContentView(R.layout.dialog_task);
        setCancelable(false);

        // ✅ NEW CODE: Move Dialog to Bottom-Right (Side Panel Style)
        Window window = getWindow();
        if (window != null) {
            WindowManager.LayoutParams layoutParams = window.getAttributes();
            // Set position to bottom and end (right side)
            layoutParams.gravity = Gravity.BOTTOM | Gravity.END;
            // Add margins so it doesn't touch the exact edge of the screen
            layoutParams.x = 40; 
            layoutParams.y = 40; 
            
            // Optional: Remove or reduce the dark background dimming if you want it to look like a notification
            // window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); 
            // layoutParams.dimAmount = 0.2f; // Very light dim
            
            window.setAttributes(layoutParams);
        }
        // ✅ END OF NEW CODE

        titleView = findViewById(R.id.title);
        taskListView = findViewById(R.id.list);
        speedView = findViewById(R.id.speed);
        cancelButton = findViewById(R.id.cancel);

        setCancel(cancel);

        cancelButton.setOnClickListener(this);

        speedEventHandler = speedEvent -> {
            String unit = "B/s";
            double speed = speedEvent.getSpeed();
            if (speed > 1024) {
                speed /= 1024;
                unit = "KB/s";
            }
            if (speed > 1024) {
                speed /= 1024;
                unit = "MB/s";
            }
            double finalSpeed = speed;
            String finalUnit = unit;
            Schedulers.androidUIThread().execute(() -> {
                speedView.setText(String.format("%.1f %s", finalSpeed, finalUnit));
            });
        };
        FileDownloadTask.speedEvent.channel(FileDownloadTask.SpeedEvent.class).registerWeak(speedEventHandler);
    }

    public void setExecutor(TaskExecutor executor) {
        setExecutor(executor, true);
    }

    public void setExecutor(TaskExecutor executor, boolean autoClose) {
        this.executor = executor;

        if (executor != null) {
            if (autoClose) {
                executor.addTaskListener(new TaskListener() {
                    @Override
                    public void onStop(boolean success, TaskExecutor executor) {
                        Schedulers.androidUIThread().execute(() -> dismiss());
                    }
                });
            }

            taskListPane = new TaskListPane(getContext(), executor);
            taskListView.setAdapter(taskListPane);
        }
    }

    public StringProperty titleProperty() {
        return titleView.stringProperty();
    }

    public String getTitle() {
        return titleView.getText().toString();
    }

    public void setTitle(String currentState) {
        titleView.setString(currentState);
    }

    public void setCancel(TaskCancellationAction onCancel) {
        this.onCancel = onCancel;

        cancelButton.setEnabled(onCancel != null);
    }

    @Override
    public void onClick(View view) {
        if (view == cancelButton) {
            Optional.ofNullable(executor).ifPresent(TaskExecutor::cancel);
            onCancel.getCancellationAction().accept(this);
            dismiss();
        }
    }
}
