package org.gregeryb.securesms.jobs;


import android.content.Context;
import android.support.annotation.NonNull;

import org.gregeryb.securesms.crypto.UnidentifiedAccessUtil;
import org.gregeryb.securesms.database.Address;
import org.gregeryb.securesms.dependencies.InjectableType;
import org.gregeryb.securesms.jobmanager.JobParameters;
import org.gregeryb.securesms.jobmanager.SafeData;
import org.gregeryb.securesms.logging.Log;
import org.gregeryb.securesms.recipients.Recipient;
import org.gregeryb.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.crypto.UntrustedIdentityException;
import org.whispersystems.signalservice.api.messages.SignalServiceReceiptMessage;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import androidx.work.Data;
import androidx.work.WorkerParameters;

public class SendReadReceiptJob extends ContextJob implements InjectableType {

  private static final long serialVersionUID = 1L;

  private static final String TAG = SendReadReceiptJob.class.getSimpleName();

  private static final String KEY_ADDRESS     = "address";
  private static final String KEY_MESSAGE_IDS = "message_ids";
  private static final String KEY_TIMESTAMP   = "timestamp";

  @Inject transient SignalServiceMessageSender messageSender;

  private String     address;
  private List<Long> messageIds;
  private long       timestamp;

  public SendReadReceiptJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  public SendReadReceiptJob(Context context, Address address, List<Long> messageIds) {
    super(context, JobParameters.newBuilder()
                                .withNetworkRequirement()
                                .create());

    this.address    = address.serialize();
    this.messageIds = messageIds;
    this.timestamp  = System.currentTimeMillis();
  }

  @Override
  protected void initialize(@NonNull SafeData data) {
    address   = data.getString(KEY_ADDRESS);
    timestamp = data.getLong(KEY_TIMESTAMP);

    long[] ids = data.getLongArray(KEY_MESSAGE_IDS);
    messageIds = new ArrayList<>(ids.length);
    for (long id : ids) {
      messageIds.add(id);
    }
  }

  @Override
  protected @NonNull Data serialize(@NonNull Data.Builder dataBuilder) {
    long[] ids = new long[messageIds.size()];
    for (int i = 0; i < ids.length; i++) {
      ids[i] = messageIds.get(i);
    }

    return dataBuilder.putString(KEY_ADDRESS, address)
                      .putLongArray(KEY_MESSAGE_IDS, ids)
                      .putLong(KEY_TIMESTAMP, timestamp)
                      .build();
  }

  @Override
  public void onRun() throws IOException, UntrustedIdentityException {
    if (!TextSecurePreferences.isReadReceiptsEnabled(context)) return;

    SignalServiceAddress        remoteAddress  = new SignalServiceAddress(address);
    SignalServiceReceiptMessage receiptMessage = new SignalServiceReceiptMessage(SignalServiceReceiptMessage.Type.READ, messageIds, timestamp);

    messageSender.sendReceipt(remoteAddress,
                              UnidentifiedAccessUtil.getAccessFor(context, Recipient.from(context, Address.fromSerialized(address), false)),
                              receiptMessage);
  }

  @Override
  public boolean onShouldRetry(Exception e) {
    if (e instanceof PushNetworkException) return true;
    return false;
  }

  @Override
  public void onCanceled() {
    Log.w(TAG, "Failed to send read receipts to: " + address);
  }
}
