package org.gregeryb.securesms.jobs;

import android.content.Context;
import android.support.annotation.NonNull;

import org.gregeryb.securesms.jobmanager.Job;
import org.gregeryb.securesms.jobmanager.JobParameters;
import org.gregeryb.securesms.jobmanager.dependencies.ContextDependent;

import androidx.work.WorkerParameters;

public abstract class ContextJob extends Job implements ContextDependent {

  protected transient Context context;

  public ContextJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  protected ContextJob(@NonNull Context context, @NonNull JobParameters parameters) {
    super(context, parameters);
    this.context = context;
  }

  public void setContext(Context context) {
    this.context = context;
  }

  protected Context getContext() {
    return context;
  }
}
