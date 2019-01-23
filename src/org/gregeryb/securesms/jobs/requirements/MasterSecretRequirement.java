package org.gregeryb.securesms.jobs.requirements;

import android.content.Context;

import org.gregeryb.securesms.jobmanager.dependencies.ContextDependent;
import org.gregeryb.securesms.jobmanager.requirements.Requirement;
import org.gregeryb.securesms.jobmanager.requirements.SimpleRequirement;
import org.gregeryb.securesms.service.KeyCachingService;

public class MasterSecretRequirement extends SimpleRequirement implements ContextDependent {

  private transient Context context;

  public MasterSecretRequirement(Context context) {
    this.context = context;
  }

  @Override
  public boolean isPresent() {
    return KeyCachingService.getMasterSecret(context) != null;
  }

  @Override
  public void setContext(Context context) {
    this.context = context;
  }
}
