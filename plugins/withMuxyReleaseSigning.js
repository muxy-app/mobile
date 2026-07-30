const {
  IOSConfig,
  withXcodeProject,
} = require('@expo/config-plugins');

module.exports = function withMuxyReleaseSigning(config) {
  return withXcodeProject(config, (mod) => {
    const projectName = IOSConfig.XcodeUtils.getProjectName(
      mod.modRequest.projectRoot,
    );
    const releaseConfiguration =
      IOSConfig.Target.getXCBuildConfigurationFromPbxproj(
        mod.modResults,
        {
          targetName: projectName,
          buildConfiguration: 'Release',
        },
      );

    if (!releaseConfiguration) {
      throw new Error(
        `Release build configuration not found for ${projectName}.`,
      );
    }

    const settings = releaseConfiguration.buildSettings;
    settings.CODE_SIGN_STYLE = 'Manual';
    settings.CODE_SIGN_IDENTITY = '"Apple Distribution"';
    settings['"CODE_SIGN_IDENTITY[sdk=iphoneos*]"'] =
      '"Apple Distribution"';
    settings.DEVELOPMENT_TEAM = '"$(MUXY_DEVELOPMENT_TEAM)"';
    settings.PROVISIONING_PROFILE_SPECIFIER =
      '"$(MUXY_PROVISIONING_PROFILE_SPECIFIER)"';

    return mod;
  });
};
