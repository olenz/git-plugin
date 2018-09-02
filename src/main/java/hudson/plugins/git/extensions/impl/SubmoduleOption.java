package hudson.plugins.git.extensions.impl;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.git.GitException;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleCombinator;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.plugins.git.extensions.GitSCMExtensionDescriptor;
import hudson.plugins.git.util.BuildData;
import java.io.IOException;
import org.jenkinsci.plugins.gitclient.GitClient;
import org.jenkinsci.plugins.gitclient.SubmoduleUpdateCommand;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Further tweak the behaviour of git-submodule.
 *
 * <p>
 * Historically, the submodule support was on by default,
 * and given the clear marker file in the source tree, I think
 * keeping this default behaviour is sensible.
 *
 * So when we split out {@link GitSCMExtension}s, we decided
 * to keep the git-submodule handling enabled by default,
 * and this extension controls the recursiveness and the option
 * to switch it off.
 *
 * @author Yury V. Zaytsev
 * @author Andrew Bayer
 * @author Kohsuke Kawaguchi
 */
public class SubmoduleOption extends GitSCMExtension {
    private boolean disableSubmodules;
    /** Use --recursive flag on submodule commands - requires git>=1.6.5 */
    private boolean recursiveSubmodules;
    /** Use --remote flag on submodule update command - requires git>=1.8.2 */
    private boolean trackingSubmodules;
    /** Use --reference flag on submodule update command - requires git>=1.6.4 */
    private String reference;
    private boolean parentCredentials;
    private Integer timeout;
    /** Use --depth flag on submodule update command - requires git>=1.8.4 */
    private boolean shallow;
    private Integer depth;

    @DataBoundConstructor
    public SubmoduleOption(boolean disableSubmodules, boolean recursiveSubmodules, boolean trackingSubmodules, String reference,Integer timeout, boolean parentCredentials) {
        this.disableSubmodules = disableSubmodules;
        this.recursiveSubmodules = recursiveSubmodules;
        this.trackingSubmodules = trackingSubmodules;
        this.parentCredentials = parentCredentials;
        this.reference = reference;
        this.timeout = timeout;
    }

    public boolean isDisableSubmodules() {
        return disableSubmodules;
    }

    public boolean isRecursiveSubmodules() {
        return recursiveSubmodules;
    }

    public boolean isTrackingSubmodules() {
        return trackingSubmodules;
    }

    public boolean isParentCredentials() {
        return parentCredentials;
    }

    public String getReference() {
        return reference;
    }

    public Integer getTimeout() {
        return timeout;
    }

    @DataBoundSetter
    public void setShallow(boolean shallow) {
        this.shallow = shallow;
    }

    public boolean getShallow() {
        return shallow;
    }

    @DataBoundSetter
    public void setDepth(Integer depth) {
        this.depth = depth;
    }

    public Integer getDepth() {
        return depth;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onClean(GitSCM scm, GitClient git) throws IOException, InterruptedException, GitException {
        if (!disableSubmodules && git.hasGitModules()) {
            git.submoduleClean(recursiveSubmodules);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCheckoutCompleted(GitSCM scm, Run<?, ?> build, GitClient git, TaskListener listener) throws IOException, InterruptedException, GitException {
        BuildData revToBuild = scm.getBuildData(build);

        try {
            if (!disableSubmodules && git.hasGitModules()) {
                // This ensures we don't miss changes to submodule paths and allows
                // seamless use of bare and non-bare superproject repositories.
                git.setupSubmoduleUrls(revToBuild.lastBuild.getRevision(), listener);
                SubmoduleUpdateCommand cmd = git.submoduleUpdate()
                        .recursive(recursiveSubmodules)
                        .remoteTracking(trackingSubmodules)
                        .parentCredentials(parentCredentials)
                        .ref(build.getEnvironment(listener).expand(reference))
                        .timeout(timeout)
                        .shallow(shallow);
                if (shallow) {
                    int usedDepth = depth == null || depth < 1 ? 1 : depth;
                    listener.getLogger().println("Using shallow submodule update with depth " + usedDepth);
                    cmd.depth(usedDepth);
                }
                cmd.execute();
            }
        } catch (GitException e) {
            // Re-throw as an IOException in order to allow generic retry
            // logic to kick in properly.
            throw new IOException("Could not perform submodule update", e);
        }

        if (scm.isDoGenerateSubmoduleConfigurations()) {
            /*
                Kohsuke Note:

                I could be wrong, but this feels like a totally wrong place to do this.
                AFAICT, SubmoduleCombinator runs a lot of git-checkout and git-commit to
                create new commits and branches. At the end of this, the working tree is
                significantly altered, and HEAD no longer points to 'revToBuild'.

                Custom BuildChooser is probably the right place to do this kind of stuff,
                or maybe we can add a separate callback for GitSCMExtension.
             */
            SubmoduleCombinator combinator = new SubmoduleCombinator(git, listener, scm.getSubmoduleCfg());
            combinator.createSubmoduleCombinations();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SubmoduleOption that = (SubmoduleOption) o;

        if (disableSubmodules != that.disableSubmodules) {
            return false;
        }
        if (recursiveSubmodules != that.recursiveSubmodules) {
            return false;
        }
        if (trackingSubmodules != that.trackingSubmodules) {
            return false;
        }
        if (parentCredentials != that.parentCredentials) {
            return false;
        }
        if (reference != null ? !reference.equals(that.reference) : that.reference != null) {
            return false;
        }
        if (timeout != null ? !timeout.equals(that.timeout) : that.timeout != null) {
            return false;
        }
        if (shallow != that.shallow) {
            return false;
        }
        if (depth != null ? !depth.equals(that.depth) : that.depth != null) {
            return false;
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return SubmoduleOption.class.hashCode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return "SubmoduleOption{" +
                "disableSubmodules=" + disableSubmodules +
                ", recursiveSubmodules=" + recursiveSubmodules +
                ", trackingSubmodules=" + trackingSubmodules +
                ", reference='" + reference + '\'' +
                ", parentCredentials=" + parentCredentials +
                ", timeout=" + timeout +
                ", shallow=" + shallow +
                ", depth=" + depth +
                '}';
    }

    @Extension
    public static class DescriptorImpl extends GitSCMExtensionDescriptor {
        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            return "Advanced sub-modules behaviours";
        }
    }
}
