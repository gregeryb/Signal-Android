package org.gregeryb.securesms.jobs.requirements;

import android.content.Context;

import org.gregeryb.securesms.jobmanager.dependencies.ContextDependent;
import org.gregeryb.securesms.jobmanager.requirements.Requirement;
import org.gregeryb.securesms.jobmanager.requirements.SimpleRequirement;
import org.gregeryb.securesms.sms.TelephonyServiceState;

public class ServiceRequirement extends SimpleRequirement implements ContextDependent {

  private static final String TAG = ServiceRequirement.class.getSimpleName();

  private transient Context context;

  public ServiceRequirement(Context context) {
    this.context  = context;
  }

  @Override
  public void setContext(Context context) {
    this.context = context;
  }

  @Override
  public boolean isPresent() {
    TelephonyServiceState telephonyServiceState = new TelephonyServiceState();
    return telephonyServiceState.isConnected(context);
  }
}
