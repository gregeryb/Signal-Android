package org.gregeryb.securesms;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;

import org.gregeryb.securesms.contactshare.Contact;
import org.gregeryb.securesms.database.model.MessageRecord;
import org.gregeryb.securesms.database.model.MmsMessageRecord;
import org.gregeryb.securesms.mms.GlideRequests;
import org.gregeryb.securesms.recipients.Recipient;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public interface BindableConversationItem extends Unbindable {
  void bind(@NonNull MessageRecord           messageRecord,
            @NonNull Optional<MessageRecord> previousMessageRecord,
            @NonNull Optional<MessageRecord> nextMessageRecord,
            @NonNull GlideRequests           glideRequests,
            @NonNull Locale                  locale,
            @NonNull Set<MessageRecord>      batchSelected,
            @NonNull Recipient               recipients,
                     boolean                 pulseHighlight);

  MessageRecord getMessageRecord();

  void setEventListener(@Nullable EventListener listener);

  interface EventListener {
    void onQuoteClicked(MmsMessageRecord messageRecord);
    void onSharedContactDetailsClicked(@NonNull Contact contact, @NonNull View avatarTransitionView);
    void onAddToContactsClicked(@NonNull Contact contact);
    void onMessageSharedContactClicked(@NonNull List<Recipient> choices);
    void onInviteSharedContactClicked(@NonNull List<Recipient> choices);
  }
}