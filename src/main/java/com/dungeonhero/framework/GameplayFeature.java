package com.dungeonhero.framework;

/** Public contract implemented by every composable gameplay module. */
public interface GameplayFeature {

  String id();

  void load(FeatureContext context, FeatureConfig config);

  void start();

  void stop();
}
