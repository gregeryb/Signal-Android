package org.gregeryb.securesms.push;

import android.content.Context;

import org.gregeryb.securesms.crypto.SecurityEvent;
import org.gregeryb.securesms.database.Address;
import org.gregeryb.securesms.database.DatabaseFactory;
import org.gregeryb.securesms.database.RecipientDatabase;
import org.gregeryb.securesms.recipients.Recipient;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;

public class SecurityEventListener implements SignalServiceMessageSender.EventListener {

  private static final String TAG = SecurityEventListener.class.getSimpleName();

  private final Context context;

  public SecurityEventListener(Context context) {
    this.context = context.getApplicationContext();
  }

  @Override
  public void onSecurityEvent(SignalServiceAddress textSecureAddress) {
    SecurityEvent.broadcastSecurityUpdateEvent(context);
  }
}
