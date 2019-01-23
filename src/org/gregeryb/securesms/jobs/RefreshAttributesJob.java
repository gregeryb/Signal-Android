package org.gregeryb.securesms.jobs;

import android.content.Context;
import android.support.annotation.NonNull;

import org.gregeryb.securesms.ApplicationContext;
import org.gregeryb.securesms.jobmanager.SafeData;
import org.gregeryb.securesms.logging.Log;

import org.gregeryb.securesms.dependencies.InjectableType;
import org.gregeryb.securesms.jobmanager.JobParameters;

import org.gregeryb.securesms.crypto.UnidentifiedAccessUtil;
import org.gregeryb.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.push.exceptions.NetworkFailureException;

import java.io.IOException;

import javax.inject.Inject;

import androidx.work.Data;
import androidx.work.WorkerParameters;

public class RefreshAttributesJob extends ContextJob implements InjectableType {

  public static final long serialVersionUID = 1L;

  private static final String TAG = RefreshAttributesJob.class.getSimpleName();

  @Inject transient SignalServiceAccountManager signalAccountManager;

  public RefreshAttributesJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  public RefreshAttributesJob(Context context) {
    super(context, JobParameters.newBuilder()
                                .withNetworkRequirement()
                                .withGroupId(RefreshAttributesJob.class.getName())
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
  public void onRun() throws IOException {
    int     registrationId              = TextSecurePreferences.getLocalRegistrationId(context);
    boolean fetchesMessages             = TextSecurePreferences.isGcmDisabled(context);
    String  pin                         = TextSecurePreferences.getRegistrationLockPin(context);
    byte[]  unidentifiedAccessKey       = UnidentifiedAccessUtil.getSelfUnidentifiedAccessKey(context);
    boolean universalUnidentifiedAccess = TextSecurePreferences.isUniversalUnidentifiedAccess(context);

    signalAccountManager.setAccountAttributes(null, registrationId, fetchesMessages, pin,
                                              unidentifiedAccessKey, universalUnidentifiedAccess);

    ApplicationContext.getInstance(context)
                      .getJobManager()
                      .add(new RefreshUnidentifiedDeliveryAbilityJob(context));
  }

  @Override
  public boolean onShouldRetry(Exception e) {
    return e instanceof NetworkFailureException;
  }

  @Override
  public void onCanceled() {
    Log.w(TAG, "Failed to update account attributes!");
  }
}
