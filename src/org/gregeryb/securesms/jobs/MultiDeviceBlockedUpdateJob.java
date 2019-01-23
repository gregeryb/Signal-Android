package org.gregeryb.securesms.jobs;

import android.content.Context;
import android.support.annotation.NonNull;

import org.gregeryb.securesms.crypto.MasterSecret;
import org.gregeryb.securesms.crypto.UnidentifiedAccessUtil;
import org.gregeryb.securesms.database.DatabaseFactory;
import org.gregeryb.securesms.database.RecipientDatabase;
import org.gregeryb.securesms.database.RecipientDatabase.RecipientReader;
import org.gregeryb.securesms.dependencies.InjectableType;
import org.gregeryb.securesms.jobmanager.JobParameters;
import org.gregeryb.securesms.jobmanager.SafeData;
import org.gregeryb.securesms.logging.Log;
import org.gregeryb.securesms.recipients.Recipient;
import org.gregeryb.securesms.util.GroupUtil;
import org.gregeryb.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.multidevice.BlockedListMessage;
import org.whispersystems.signalservice.api.messages.multidevice.SignalServiceSyncMessage;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;

import androidx.work.Data;
import androidx.work.WorkerParameters;

public class MultiDeviceBlockedUpdateJob extends ContextJob implements InjectableType {

  private static final long serialVersionUID = 1L;

  @SuppressWarnings("unused")
  private static final String TAG = MultiDeviceBlockedUpdateJob.class.getSimpleName();

  @Inject transient SignalServiceMessageSender messageSender;

  public MultiDeviceBlockedUpdateJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  public MultiDeviceBlockedUpdateJob(Context context) {
    super(context, JobParameters.newBuilder()
                                .withNetworkRequirement()
                                .withGroupId(MultiDeviceBlockedUpdateJob.class.getSimpleName())
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
  public void onRun()
      throws IOException, UntrustedIdentityException
  {
    if (!TextSecurePreferences.isMultiDevice(context)) {
      Log.i(TAG, "Not multi device, aborting...");
      return;
    }

    RecipientDatabase database = DatabaseFactory.getRecipientDatabase(context);

    try (RecipientReader reader = database.readerForBlocked(database.getBlocked())) {
      List<String> blockedIndividuals = new LinkedList<>();
      List<byte[]> blockedGroups      = new LinkedList<>();

      Recipient recipient;

      while ((recipient = reader.getNext()) != null) {
        if (recipient.isGroupRecipient()) {
          blockedGroups.add(GroupUtil.getDecodedId(recipient.getAddress().toGroupString()));
        } else {
          blockedIndividuals.add(recipient.getAddress().serialize());
        }
      }

      messageSender.sendMessage(SignalServiceSyncMessage.forBlocked(new BlockedListMessage(blockedIndividuals, blockedGroups)),
                                UnidentifiedAccessUtil.getAccessForSync(context));
    }
  }

  @Override
  public boolean onShouldRetry(Exception exception) {
    if (exception instanceof PushNetworkException) return true;
    return false;
  }

  @Override
  public void onCanceled() {

  }
}
