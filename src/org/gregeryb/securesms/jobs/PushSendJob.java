package org.gregeryb.securesms.jobs;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.annimon.stream.Stream;

import org.greenrobot.eventbus.EventBus;
import org.signal.libsignal.metadata.certificate.InvalidCertificateException;
import org.signal.libsignal.metadata.certificate.SenderCertificate;
import org.gregeryb.securesms.ApplicationContext;
import org.gregeryb.securesms.TextSecureExpiredException;
import org.gregeryb.securesms.attachments.Attachment;
import org.gregeryb.securesms.contactshare.Contact;
import org.gregeryb.securesms.contactshare.ContactModelMapper;
import org.gregeryb.securesms.crypto.ProfileKeyUtil;
import org.gregeryb.securesms.database.Address;
import org.gregeryb.securesms.database.DatabaseFactory;
import org.gregeryb.securesms.events.PartProgressEvent;
import org.gregeryb.securesms.jobmanager.JobParameters;
import org.gregeryb.securesms.logging.Log;
import org.gregeryb.securesms.mms.DecryptableStreamUriLoader;
import org.gregeryb.securesms.mms.OutgoingMediaMessage;
import org.gregeryb.securesms.mms.PartAuthority;
import org.gregeryb.securesms.notifications.MessageNotifier;
import org.gregeryb.securesms.recipients.Recipient;
import org.gregeryb.securesms.util.Base64;
import org.gregeryb.securesms.util.BitmapDecodingException;
import org.gregeryb.securesms.util.BitmapUtil;
import org.gregeryb.securesms.util.MediaUtil;
import org.gregeryb.securesms.util.TextSecurePreferences;
import org.gregeryb.securesms.util.Util;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachment;
import org.whispersystems.signalservice.api.messages.SignalServiceAttachmentPointer;
import org.whispersystems.signalservice.api.messages.SignalServiceDataMessage;
import org.whispersystems.signalservice.api.messages.shared.SharedContact;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.work.WorkerParameters;

public abstract class PushSendJob extends SendJob {

  private static final long   serialVersionUID              = 5906098204770900739L;
  private static final String TAG                           = PushSendJob.class.getSimpleName();
  private static final long   CERTIFICATE_EXPIRATION_BUFFER = TimeUnit.DAYS.toMillis(1);

  protected  PushSendJob(@NonNull Context context, @NonNull WorkerParameters workerParameters) {
    super(context, workerParameters);
  }

  protected PushSendJob(Context context, JobParameters parameters) {
    super(context, parameters);
  }

  protected static JobParameters constructParameters(Address destination) {
    JobParameters.Builder builder = JobParameters.newBuilder();
    builder.withGroupId(destination.serialize());
    builder.withNetworkRequirement();
    builder.withRetryDuration(TimeUnit.DAYS.toMillis(1));

    return builder.create();
  }

  @Override
  protected final void onSend() throws Exception {
    if (TextSecurePreferences.getSignedPreKeyFailureCount(context) > 5) {
      ApplicationContext.getInstance(context)
                        .getJobManager()
                        .add(new RotateSignedPreKeyJob(context));

      throw new TextSecureExpiredException("Too many signed prekey rotation failures");
    }

    onPushSend();
  }

  @Override
  public void onRetry() {
    super.onRetry();
    Log.i(TAG, "onRetry()");

    if (getRunAttemptCount() > 1) {
      Log.i(TAG, "Scheduling service outage detection job.");
      ApplicationContext.getInstance(context).getJobManager().add(new ServiceOutageDetectionJob(context));
    }
  }

  protected Optional<byte[]> getProfileKey(@NonNull Recipient recipient) {
    if (!recipient.resolve().isSystemContact() && !recipient.resolve().isProfileSharing()) {
      return Optional.absent();
    }

    return Optional.of(ProfileKeyUtil.getProfileKey(context));
  }

  protected SignalServiceAddress getPushAddress(Address address) {
    String relay = null;
    return new SignalServiceAddress(address.toPhoneString(), Optional.fromNullable(relay));
  }

  protected List<SignalServiceAttachment> getAttachmentsFor(List<Attachment> parts) {
    List<SignalServiceAttachment> attachments = new LinkedList<>();

    for (final Attachment attachment : parts) {
      SignalServiceAttachment converted = getAttachmentFor(attachment);
      if (converted != null) {
        attachments.add(converted);
      }
    }

    return attachments;
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

  protected @NonNull List<SignalServiceAttachment> getAttachmentPointersFor(List<Attachment> attachments) {
    return Stream.of(attachments).map(this::getAttachmentPointerFor).filter(a -> a != null).toList();
  }

  protected @Nullable SignalServiceAttachment getAttachmentPointerFor(Attachment attachment) {
    if (TextUtils.isEmpty(attachment.getLocation())) {
      Log.w(TAG, "empty content id");
      return null;
    }

    if (TextUtils.isEmpty(attachment.getKey())) {
      Log.w(TAG, "empty encrypted key");
      return null;
    }

    try {
      long   id  = Long.parseLong(attachment.getLocation());
      byte[] key = Base64.decode(attachment.getKey());

      return new SignalServiceAttachmentPointer(id,
                                                attachment.getContentType(),
                                                key,
                                                Optional.of(Util.toIntExact(attachment.getSize())),
                                                Optional.absent(),
                                                attachment.getWidth(),
                                                attachment.getHeight(),
                                                Optional.fromNullable(attachment.getDigest()),
                                                Optional.fromNullable(attachment.getFileName()),
                                                attachment.isVoiceNote(),
                                                Optional.fromNullable(attachment.getCaption()));
    } catch (IOException | ArithmeticException e) {
      Log.w(TAG, e);
      return null;
    }
  }

  protected static void notifyMediaMessageDeliveryFailed(Context context, long messageId) {
    long      threadId  = DatabaseFactory.getMmsDatabase(context).getThreadIdForMessage(messageId);
    Recipient recipient = DatabaseFactory.getThreadDatabase(context).getRecipientForThreadId(threadId);

    if (threadId != -1 && recipient != null) {
      MessageNotifier.notifyMessageDeliveryFailed(context, recipient, threadId);
    }
  }

  protected Optional<SignalServiceDataMessage.Quote> getQuoteFor(OutgoingMediaMessage message) {
    if (message.getOutgoingQuote() == null) return Optional.absent();

    long                                                  quoteId          = message.getOutgoingQuote().getId();
    String                                                quoteBody        = message.getOutgoingQuote().getText();
    Address                                               quoteAuthor      = message.getOutgoingQuote().getAuthor();
    List<SignalServiceDataMessage.Quote.QuotedAttachment> quoteAttachments = new LinkedList<>();

    for (Attachment attachment : message.getOutgoingQuote().getAttachments()) {
      BitmapUtil.ScaleResult  thumbnailData = null;
      SignalServiceAttachment thumbnail     = null;

      try {
        if (MediaUtil.isImageType(attachment.getContentType()) && attachment.getDataUri() != null) {
          thumbnailData = BitmapUtil.createScaledBytes(context, new DecryptableStreamUriLoader.DecryptableUri(attachment.getDataUri()), 100, 100, 500 * 1024);
        } else if (MediaUtil.isVideoType(attachment.getContentType()) && attachment.getThumbnailUri() != null) {
          thumbnailData = BitmapUtil.createScaledBytes(context, new DecryptableStreamUriLoader.DecryptableUri(attachment.getThumbnailUri()), 100, 100, 500 * 1024);
        }

        if (thumbnailData != null) {
          thumbnail = SignalServiceAttachment.newStreamBuilder()
                                             .withContentType("image/jpeg")
                                             .withWidth(thumbnailData.getWidth())
                                             .withHeight(thumbnailData.getHeight())
                                             .withLength(thumbnailData.getBitmap().length)
                                             .withStream(new ByteArrayInputStream(thumbnailData.getBitmap()))
                                             .build();
        }

        quoteAttachments.add(new SignalServiceDataMessage.Quote.QuotedAttachment(attachment.getContentType(),
                                                                                 attachment.getFileName(),
                                                                                 thumbnail));
      } catch (BitmapDecodingException e) {
        Log.w(TAG, e);
      }
    }

    return Optional.of(new SignalServiceDataMessage.Quote(quoteId, new SignalServiceAddress(quoteAuthor.serialize()), quoteBody, quoteAttachments));
  }

  List<SharedContact> getSharedContactsFor(OutgoingMediaMessage mediaMessage) {
    List<SharedContact> sharedContacts = new LinkedList<>();

    for (Contact contact : mediaMessage.getSharedContacts()) {
      SharedContact.Builder builder = ContactModelMapper.localToRemoteBuilder(contact);
      SharedContact.Avatar  avatar  = null;

      if (contact.getAvatar() != null && contact.getAvatar().getAttachment() != null) {
        avatar = SharedContact.Avatar.newBuilder().withAttachment(getAttachmentFor(contact.getAvatarAttachment()))
                                                  .withProfileFlag(contact.getAvatar().isProfile())
                                                  .build();
      }

      builder.setAvatar(avatar);
      sharedContacts.add(builder.build());
    }

    return sharedContacts;
  }

  protected void rotateSenderCertificateIfNecessary() throws IOException {
    try {
      byte[] certificateBytes = TextSecurePreferences.getUnidentifiedAccessCertificate(context);

      if (certificateBytes == null) {
        throw new InvalidCertificateException("No certificate was present.");
      }

      SenderCertificate certificate = new SenderCertificate(certificateBytes);

      if (System.currentTimeMillis() > (certificate.getExpiration() - CERTIFICATE_EXPIRATION_BUFFER)) {
        throw new InvalidCertificateException("Certificate is expired, or close to it. Expires on: " + certificate.getExpiration() + ", currently: " + System.currentTimeMillis());
      }

      Log.d(TAG, "Certificate is valid.");
    } catch (InvalidCertificateException e) {
      Log.w(TAG, "Certificate was invalid at send time. Fetching a new one.", e);
      RotateCertificateJob certificateJob = new RotateCertificateJob(context);
      ApplicationContext.getInstance(context).injectDependencies(certificateJob);
      certificateJob.setContext(context);
      certificateJob.onRun();
    }
  }

  protected abstract void onPushSend() throws Exception;
}