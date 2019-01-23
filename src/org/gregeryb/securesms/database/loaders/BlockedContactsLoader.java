package org.gregeryb.securesms.database.loaders;

import android.content.Context;
import android.database.Cursor;

import org.gregeryb.securesms.database.DatabaseFactory;
import org.gregeryb.securesms.util.AbstractCursorLoader;

public class BlockedContactsLoader extends AbstractCursorLoader {

  public BlockedContactsLoader(Context context) {
    super(context);
  }

  @Override
  public Cursor getCursor() {
    return DatabaseFactory.getRecipientDatabase(getContext())
                          .getBlocked();
  }

}
