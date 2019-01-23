package org.gregeryb.securesms.jobs.requirements;


import android.content.Context;
import android.support.annotation.NonNull;

import org.gregeryb.securesms.jobmanager.dependencies.ContextDependent;
import org.gregeryb.securesms.jobmanager.requirements.Requirement;
import org.gregeryb.securesms.jobmanager.requirements.SimpleRequirement;
import org.gregeryb.securesms.util.TextSecurePreferences;

public class SqlCipherMigrationRequirement extends SimpleRequirement implements ContextDependent {

  @SuppressWarnings("unused")
  private static final String TAG = SqlCipherMigrationRequirement.class.getSimpleName();

  private transient Context context;

  public SqlCipherMigrationRequirement(@NonNull Context context) {
    this.context = context;
  }

  @Override
  public void setContext(Context context) {
    this.context = context;
  }

  @Override
  public boolean isPresent() {
    return !TextSecurePreferences.getNeedsSqlCipherMigration(context);
  }
}
