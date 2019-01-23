/*
 * Copyright (C) 2016 Open Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.gregeryb.securesms.notifications;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.RemoteInput;

import org.gregeryb.securesms.database.Address;
import org.gregeryb.securesms.database.DatabaseFactory;
import org.gregeryb.securesms.database.MessagingDatabase.MarkedMessageInfo;
import org.gregeryb.securesms.database.RecipientDatabase;
import org.gregeryb.securesms.mms.OutgoingMediaMessage;
import org.gregeryb.securesms.recipients.Recipient;
import org.gregeryb.securesms.sms.MessageSender;
import org.gregeryb.securesms.sms.OutgoingEncryptedMessage;
import org.gregeryb.securesms.sms.OutgoingTextMessage;
import org.gregeryb.securesms.util.TextSecurePreferences;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Get the response text from the Wearable Device and sends an message as a reply
 */
public class RemoteReplyReceiver extends BroadcastReceiver {

  public static final String TAG           = RemoteReplyReceiver.class.getSimpleName();
  public static final String REPLY_ACTION  = "org.gregeryb.securesms.notifications.WEAR_REPLY";
  public static final String ADDRESS_EXTRA = "address";

  @SuppressLint("StaticFieldLeak")
  @Override
  public void onReceive(final Context context, Intent intent) {
    if (!REPLY_ACTION.equals(intent.getAction())) return;

    Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);

    if (remoteInput == null) return;

    final Address      address      = intent.getParcelableExtra(ADDRESS_EXTRA);
    final CharSequence responseText = remoteInput.getCharSequence(MessageNotifier.EXTRA_REMOTE_REPLY);

    if (responseText != null) {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          long threadId;

          Recipient recipient = Recipient.from(context, address, false);
          int  subscriptionId = recipient.getDefaultSubscriptionId().or(-1);
          long expiresIn      = recipient.getExpireMessages() * 1000L;

          if (recipient.isGroupRecipient()) {
            OutgoingMediaMessage reply = new OutgoingMediaMessage(recipient, responseText.toString(), new LinkedList<>(), System.currentTimeMillis(), subscriptionId, expiresIn, 0, null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
            threadId = MessageSender.send(context, reply, -1, false, null);
          } else if (TextSecurePreferences.isPushRegistered(context) && recipient.getRegistered() == RecipientDatabase.RegisteredState.REGISTERED) {
            OutgoingEncryptedMessage reply = new OutgoingEncryptedMessage(recipient, responseText.toString(), expiresIn);
            threadId = MessageSender.send(context, reply, -1, false, null);
          } else {
            OutgoingTextMessage reply = new OutgoingTextMessage(recipient, responseText.toString(), expiresIn, subscriptionId);
            threadId = MessageSender.send(context, reply, -1, false, null);
          }

          List<MarkedMessageInfo> messageIds = DatabaseFactory.getThreadDatabase(context).setRead(threadId, true);

          MessageNotifier.updateNotification(context);
          MarkReadReceiver.process(context, messageIds);

          return null;
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

  }
}
