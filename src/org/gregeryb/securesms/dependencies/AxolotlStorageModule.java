package org.gregeryb.securesms.dependencies;

import android.content.Context;

import org.gregeryb.securesms.crypto.storage.SignalProtocolStoreImpl;
import org.gregeryb.securesms.jobs.CleanPreKeysJob;
import org.whispersystems.libsignal.state.SignedPreKeyStore;

import dagger.Module;
import dagger.Provides;

@Module (complete = false, injects = {CleanPreKeysJob.class})
public class AxolotlStorageModule {

  private final Context context;

  public AxolotlStorageModule(Context context) {
    this.context = context;
  }

  @Provides SignedPreKeyStoreFactory provideSignedPreKeyStoreFactory() {
    return new SignedPreKeyStoreFactory() {
      @Override
      public SignedPreKeyStore create() {
        return new SignalProtocolStoreImpl(context);
      }
    };
  }

  public static interface SignedPreKeyStoreFactory {
    public SignedPreKeyStore create();
  }
}