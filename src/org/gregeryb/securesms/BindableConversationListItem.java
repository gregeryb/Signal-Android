package org.gregeryb.securesms;

import android.support.annotation.NonNull;

import org.gregeryb.securesms.crypto.MasterSecret;
import org.gregeryb.securesms.database.model.ThreadRecord;
import org.gregeryb.securesms.mms.GlideRequests;

import java.util.Locale;
import java.util.Set;

public interface BindableConversationListItem extends Unbindable {

  public void bind(@NonNull ThreadRecord thread,
                   @NonNull GlideRequests glideRequests, @NonNull Locale locale,
                   @NonNull Set<Long> typingThreads,
                   @NonNull Set<Long> selectedThreads, boolean batchMode);
}
