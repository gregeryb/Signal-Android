package org.gregeryb.securesms.jobs;

import android.content.Context;
import android.support.annotation.NonNull;

import org.greenrobot.eventbus.EventBus;
import org.gregeryb.securesms.attachments.Attachment;
import org.gregeryb.securesms.attachments.AttachmentId;
import org.gregeryb.securesms.attachments.DatabaseAttachment;
import org.gregeryb.securesms.attachments.PointerAttachment;
import org.gregeryb.securesms.database.AttachmentDatabase;
import org.gregeryb.securesms.database.DatabaseFactory;
import org.gregeryb.securesms.dependencies.InjectableType;
import org.gregeryb.securesms.events.PartProgressEvent;
import org.gregeryb.securesms.jobmanager.JobParameters;
import org.gregeryb.securesms.jobmanager.SafeData;
import org.gregeryb.securesms.logging.Log;
import org.gregeryb.securesms.mms.MediaConstraints;
import org.gregeryb.securesms.mms.MediaStream;
import org.gregeryb.securesms.mms.MmsException;
import org.gregeryb.securesms.mms.PartAuthority;
import org.gregeryb.securesms.transport.UndeliverableMessageException;
import org.gregeryb.securesms.util.MediaUtil;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.net.ssl.SSLException;

import androidx.work.Data;
import androidx.work.WorkerParameters;

public class AttachmentUploadJob extends ContextJob implements InjectableType {

  private static final String TAG = AttachmentUploadJob.class.getSimpleName();

  private static final String KEY_ROW_ID    = "row_id";
  private static final String KEY_UNIQUE_ID = "unique_id";

  private AttachmentId               attachmentId;
  @Inject SignalServiceMessageSender messageSender;

  public AttachmentUploadJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  protected AttachmentUploadJob(@NonNull Context context, AttachmentId attachmentId) {
    super(context, new JobParameters.Builder()
                                    .withNetworkRequirement()
                                    .withRetryDuration(TimeUnit.DAYS.toMillis(1))
                                    .create());

    this.attachmentId = attachmentId;
  }

  @Override
  protected void initialize(@NonNull SafeData data) {
    this.attachmentId = new AttachmentId(data.getLong(KEY_ROW_ID), data.getLong(KEY_UNIQUE_ID));
  }

  @Override
  protected @NonNull Data serialize(@NonNull Data.Builder dataBuilder) {
    return dataBuilder.putLong(KEY_ROW_ID, attachmentId.getRowId())
                      .putLong(KEY_UNIQUE_ID, attachmentId.getUniqueId())
                      .build();
  }

  @Override
  public void onRun() throws Exception {
    AttachmentDatabase database           = DatabaseFactory.getAttachmentDatabase(context);
    DatabaseAttachment databaseAttachment = database.getAttachment(attachmentId);

    if (databaseAttachment == null) {
      throw new IllegalStateException("Cannot find the specified attachment.");
    }

    MediaConstraints               mediaConstraints = MediaConstraints.getPushMediaConstraints();
    Attachment                     scaledAttachment = scaleAndStripExif(database, mediaConstraints, databaseAttachment);
    SignalServiceAttachment        localAttachment  = getAttachmentFor(scaledAttachment);
    SignalServiceAttachmentPointer remoteAttachment = messageSender.uploadAttachment(localAttachment.asStream());
    Attachment                     attachment       = PointerAttachment.forPointer(Optional.of(remoteAttachment)).get();

    database.updateAttachmentAfterUpload(databaseAttachment.getAttachmentId(), attachment);
  }

  @Override
  protected void onCanceled() { }

  @Override
  protected boolean onShouldRetry(Exception exception) {
    return exception instanceof PushNetworkException ||
           exception instanceof SSLException         ||
           exception instanceof ConnectException;
  }

  protected SignalServiceAttachment getAttachmentFor(Attachment attachment) {
    try {
      if (attachment.getDataUri() == null || attachment.getSize() == 0) throw new IOException("Assertion failed, outgoing attachment has no data!");
      InputStream is = PartAuthority.getAttachmentStream(context, attachment.getDataUri());
      return SignalServiceAttachment.newStreamBuilder()
                                    .withStream(is)
                                    .withContentType(attachment.getContentType())
                                    .withLength(attachment.getSize())
                                    .withFileName(attachment.getFileName())
                                    .withVoiceNote(attachment.isVoiceNote())
                                    .withWidth(attachment.getWidth())
                                    .withHeight(attachment.getHeight())
                                    .withCaption(attachment.getCaption())
                                    .withListener((total, progress) -> EventBus.getDefault().postSticky(new PartProgressEvent(attachment, total, progress)))
                                    .build();
    } catch (IOException ioe) {
      Log.w(TAG, "Couldn't open attachment", ioe);
    }
    return null;
  }

  private Attachment scaleAndStripExif(@NonNull AttachmentDatabase attachmentDatabase,
                                       @NonNull MediaConstraints constraints,
                                       @NonNull Attachment attachment)
      throws UndeliverableMessageException
  {
    try {
      if (constraints.isSatisfied(context, attachment)) {
        if (MediaUtil.isJpeg(attachment)) {
          MediaStream stripped = constraints.getResizedMedia(context, attachment);
          return attachmentDatabase.updateAttachmentData(attachment, stripped);
        } else {
          return attachment;
        }
      } else if (constraints.canResize(attachment)) {
        MediaStream resized = constraints.getResizedMedia(context, attachment);
        return attachmentDatabase.updateAttachmentData(attachment, resized);
      } else {
        throw new UndeliverableMessageException("Size constraints could not be met!");
      }
    } catch (IOException | MmsException e) {
      throw new UndeliverableMessageException(e);
    }
  }
}
