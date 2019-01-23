package org.gregeryb.securesms.dependencies;

import android.content.Context;

import org.gregeryb.securesms.gcm.GcmBroadcastReceiver;
import org.gregeryb.securesms.jobs.AttachmentUploadJob;
import org.gregeryb.securesms.jobs.MultiDeviceConfigurationUpdateJob;
import org.gregeryb.securesms.jobs.RefreshUnidentifiedDeliveryAbilityJob;
import org.gregeryb.securesms.jobs.RotateProfileKeyJob;
import org.gregeryb.securesms.jobs.TypingSendJob;
import org.gregeryb.securesms.logging.Log;

import org.greenrobot.eventbus.EventBus;
import org.gregeryb.securesms.BuildConfig;
import org.gregeryb.securesms.CreateProfileActivity;
import org.gregeryb.securesms.DeviceListFragment;
import org.gregeryb.securesms.crypto.storage.SignalProtocolStoreImpl;
import org.gregeryb.securesms.events.ReminderUpdateEvent;
import org.gregeryb.securesms.jobs.AttachmentDownloadJob;
import org.gregeryb.securesms.jobs.AvatarDownloadJob;
import org.gregeryb.securesms.jobs.CleanPreKeysJob;
import org.gregeryb.securesms.jobs.CreateSignedPreKeyJob;
import org.gregeryb.securesms.jobs.GcmRefreshJob;
import org.gregeryb.securesms.jobs.MultiDeviceBlockedUpdateJob;
import org.gregeryb.securesms.jobs.MultiDeviceContactUpdateJob;
import org.gregeryb.securesms.jobs.MultiDeviceGroupUpdateJob;
import org.gregeryb.securesms.jobs.MultiDeviceProfileKeyUpdateJob;
import org.gregeryb.securesms.jobs.MultiDeviceReadReceiptUpdateJob;
import org.gregeryb.securesms.jobs.MultiDeviceReadUpdateJob;
import org.gregeryb.securesms.jobs.MultiDeviceVerifiedUpdateJob;
import org.gregeryb.securesms.jobs.PushGroupSendJob;
import org.gregeryb.securesms.jobs.PushGroupUpdateJob;
import org.gregeryb.securesms.jobs.PushMediaSendJob;
import org.gregeryb.securesms.jobs.PushNotificationReceiveJob;
import org.gregeryb.securesms.jobs.PushTextSendJob;
import org.gregeryb.securesms.jobs.RefreshAttributesJob;
import org.gregeryb.securesms.jobs.RefreshPreKeysJob;
import org.gregeryb.securesms.jobs.RequestGroupInfoJob;
import org.gregeryb.securesms.jobs.RetrieveProfileAvatarJob;
import org.gregeryb.securesms.jobs.RetrieveProfileJob;
import org.gregeryb.securesms.jobs.RotateCertificateJob;
import org.gregeryb.securesms.jobs.RotateSignedPreKeyJob;
import org.gregeryb.securesms.jobs.SendDeliveryReceiptJob;
import org.gregeryb.securesms.jobs.SendReadReceiptJob;
import org.gregeryb.securesms.preferences.AppProtectionPreferenceFragment;
import org.gregeryb.securesms.push.SecurityEventListener;
import org.gregeryb.securesms.push.SignalServiceNetworkAccess;
import org.gregeryb.securesms.service.IncomingMessageObserver;
import org.gregeryb.securesms.service.WebRtcCallService;
import org.gregeryb.securesms.util.TextSecurePreferences;
import org.whispersystems.libsignal.util.guava.Optional;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.api.util.RealtimeSleepTimer;
import org.whispersystems.signalservice.api.util.SleepTimer;
import org.whispersystems.signalservice.api.util.UptimeSleepTimer;
import org.whispersystems.signalservice.api.websocket.ConnectivityListener;

import dagger.Module;
import dagger.Provides;

@Module(complete = false, injects = {CleanPreKeysJob.class,
                                     CreateSignedPreKeyJob.class,
                                     PushGroupSendJob.class,
                                     PushTextSendJob.class,
                                     PushMediaSendJob.class,
                                     AttachmentDownloadJob.class,
                                     RefreshPreKeysJob.class,
                                     IncomingMessageObserver.class,
                                     PushNotificationReceiveJob.class,
                                     MultiDeviceContactUpdateJob.class,
                                     MultiDeviceGroupUpdateJob.class,
                                     MultiDeviceReadUpdateJob.class,
                                     MultiDeviceBlockedUpdateJob.class,
                                     DeviceListFragment.class,
                                     RefreshAttributesJob.class,
                                     GcmRefreshJob.class,
                                     RequestGroupInfoJob.class,
                                     PushGroupUpdateJob.class,
                                     AvatarDownloadJob.class,
                                     RotateSignedPreKeyJob.class,
                                     WebRtcCallService.class,
                                     RetrieveProfileJob.class,
                                     MultiDeviceVerifiedUpdateJob.class,
                                     CreateProfileActivity.class,
                                     RetrieveProfileAvatarJob.class,
                                     MultiDeviceProfileKeyUpdateJob.class,
                                     SendReadReceiptJob.class,
                                     MultiDeviceReadReceiptUpdateJob.class,
                                     AppProtectionPreferenceFragment.class,
                                     GcmBroadcastReceiver.class,
                                     RotateCertificateJob.class,
                                     SendDeliveryReceiptJob.class,
                                     RotateProfileKeyJob.class,
                                     MultiDeviceConfigurationUpdateJob.class,
                                     RefreshUnidentifiedDeliveryAbilityJob.class,
                                     TypingSendJob.class,
                                     AttachmentUploadJob.class})
public class SignalCommunicationModule {

  private static final String TAG = SignalCommunicationModule.class.getSimpleName();

  private final Context                      context;
  private final SignalServiceNetworkAccess   networkAccess;

  private SignalServiceAccountManager  accountManager;
  private SignalServiceMessageSender   messageSender;
  private SignalServiceMessageReceiver messageReceiver;

  public SignalCommunicationModule(Context context, SignalServiceNetworkAccess networkAccess) {
    this.context       = context;
    this.networkAccess = networkAccess;
  }

  @Provides
  synchronized SignalServiceAccountManager provideSignalAccountManager() {
    if (this.accountManager == null) {
      this.accountManager = new SignalServiceAccountManager(networkAccess.getConfiguration(context),
                                                            new DynamicCredentialsProvider(context),
                                                            BuildConfig.USER_AGENT);
    }

    return this.accountManager;
  }

  @Provides
  synchronized SignalServiceMessageSender provideSignalMessageSender() {
    if (this.messageSender == null) {
      this.messageSender = new SignalServiceMessageSender(networkAccess.getConfiguration(context),
                                                          new DynamicCredentialsProvider(context),
                                                          new SignalProtocolStoreImpl(context),
                                                          BuildConfig.USER_AGENT,
                                                          TextSecurePreferences.isMultiDevice(context),
                                                          Optional.fromNullable(IncomingMessageObserver.getPipe()),
                                                          Optional.fromNullable(IncomingMessageObserver.getUnidentifiedPipe()),
                                                          Optional.of(new SecurityEventListener(context)));
    } else {
      this.messageSender.setMessagePipe(IncomingMessageObserver.getPipe(), IncomingMessageObserver.getUnidentifiedPipe());
      this.messageSender.setIsMultiDevice(TextSecurePreferences.isMultiDevice(context));
    }

    return this.messageSender;
  }

  @Provides
  synchronized SignalServiceMessageReceiver provideSignalMessageReceiver() {
    if (this.messageReceiver == null) {
      SleepTimer sleepTimer =  TextSecurePreferences.isGcmDisabled(context) ? new RealtimeSleepTimer(context) : new UptimeSleepTimer();

      this.messageReceiver = new SignalServiceMessageReceiver(networkAccess.getConfiguration(context),
                                                              new DynamicCredentialsProvider(context),
                                                              BuildConfig.USER_AGENT,
                                                              new PipeConnectivityListener(),
                                                              sleepTimer);
    }

    return this.messageReceiver;
  }

  @Provides
  synchronized SignalServiceNetworkAccess provideSignalServiceNetworkAccess() {
    return networkAccess;
  }

  private static class DynamicCredentialsProvider implements CredentialsProvider {

    private final Context context;

    private DynamicCredentialsProvider(Context context) {
      this.context = context.getApplicationContext();
    }

    @Override
    public String getUser() {
      return TextSecurePreferences.getLocalNumber(context);
    }

    @Override
    public String getPassword() {
      return TextSecurePreferences.getPushServerPassword(context);
    }

    @Override
    public String getSignalingKey() {
      return TextSecurePreferences.getSignalingKey(context);
    }
  }

  private class PipeConnectivityListener implements ConnectivityListener {

    @Override
    public void onConnected() {
      Log.i(TAG, "onConnected()");
    }

    @Override
    public void onConnecting() {
      Log.i(TAG, "onConnecting()");
    }

    @Override
    public void onDisconnected() {
      Log.w(TAG, "onDisconnected()");
    }

    @Override
    public void onAuthenticationFailure() {
      Log.w(TAG, "onAuthenticationFailure()");
      TextSecurePreferences.setUnauthorizedReceived(context, true);
      EventBus.getDefault().post(new ReminderUpdateEvent());
    }

  }

}
