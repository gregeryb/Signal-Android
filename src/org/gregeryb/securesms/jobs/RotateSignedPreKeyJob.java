package org.gregeryb.securesms.jobs;


import android.content.Context;
import android.support.annotation.NonNull;

import org.gregeryb.securesms.ApplicationContext;
import org.gregeryb.securesms.crypto.IdentityKeyUtil;
import org.gregeryb.securesms.crypto.MasterSecret;
import org.gregeryb.securesms.crypto.PreKeyUtil;
import org.gregeryb.securesms.dependencies.InjectableType;
import org.gregeryb.securesms.jobmanager.JobParameters;
import org.gregeryb.securesms.jobmanager.SafeData;
import org.gregeryb.securesms.logging.Log;
import org.gregeryb.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.IdentityKeyPair;
import org.whispersystems.libsignal.state.SignedPreKeyRecord;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import javax.inject.Inject;

import androidx.work.Data;
import androidx.work.WorkerParameters;

public class RotateSignedPreKeyJob extends ContextJob implements InjectableType {

  private static final String TAG = RotateSignedPreKeyJob.class.getSimpleName();

  @Inject transient SignalServiceAccountManager accountManager;

  public RotateSignedPreKeyJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  public RotateSignedPreKeyJob(Context context) {
    super(context, JobParameters.newBuilder()
                                .withNetworkRequirement()
                                .withRetryCount(5)
                                .create());
  }

  @Override
  protected void initialize(@NonNull SafeData data) {
  }

  @Override
  protected @NonNull Data serialize(@NonNull Data.Builder dataBuilder) {
    return dataBuilder.build();
  }

  @Override
  public void onRun() throws Exception {
    Log.i(TAG, "Rotating signed prekey...");

    IdentityKeyPair    identityKey        = IdentityKeyUtil.getIdentityKeyPair(context);
    SignedPreKeyRecord signedPreKeyRecord = PreKeyUtil.generateSignedPreKey(context, identityKey, false);

    accountManager.setSignedPreKey(signedPreKeyRecord);

    PreKeyUtil.setActiveSignedPreKeyId(context, signedPreKeyRecord.getId());
    TextSecurePreferences.setSignedPreKeyRegistered(context, true);
    TextSecurePreferences.setSignedPreKeyFailureCount(context, 0);

    ApplicationContext.getInstance(context)
                      .getJobManager()
                      .add(new CleanPreKeysJob(context));
  }

  @Override
  public boolean onShouldRetry(Exception exception) {
    return exception instanceof PushNetworkException;
  }

  @Override
  public void onCanceled() {
    TextSecurePreferences.setSignedPreKeyFailureCount(context, TextSecurePreferences.getSignedPreKeyFailureCount(context) + 1);
  }
}
