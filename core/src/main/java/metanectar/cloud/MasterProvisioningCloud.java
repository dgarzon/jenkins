package metanectar.cloud;

import com.google.common.util.concurrent.ForwardingFuture;
import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.model.Node;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import metanectar.model.MetaNectar;
import metanectar.provisioning.MasterProvisioningNodeProperty;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Paul Sandoz
 */
public class MasterProvisioningCloud extends AbstractProvisioningCloud {

    private final Cloud c;

    private final MasterProvisioningNodeProperty mpnp;

    @DataBoundConstructor
    public MasterProvisioningCloud(Cloud c, MasterProvisioningNodeProperty mpnp) {
        super("master-provisioning-" + c.name);
        this.c = c;
        this.mpnp = mpnp;
    }


    @Override
    public Collection<NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {
        final Collection<NodeProvisioner.PlannedNode> delegatedNodes = c.provision(label, excessWorkload);
        final Collection<NodeProvisioner.PlannedNode> pns = new ArrayList<NodeProvisioner.PlannedNode>(delegatedNodes.size());

        for (final NodeProvisioner.PlannedNode delegated : delegatedNodes) {
            pns.add(new NodeProvisioner.PlannedNode(delegated.displayName, adapt(delegated.future), delegated.numExecutors));
        }

        return pns;
    }

    @Override
    public boolean canProvision(Label label) {
        // TODO consider supporting labels of cloud as a refinement for master provisioning
        return label.equals(MetaNectar.getInstance().masterProvisioner.masterLabel);
    }

    private Future<Node> adapt(final Future<Node> fn) {
        return new ForwardingFuture<Node>() {
            @Override
            protected Future<Node> delegate() {
                return fn;
            }

            @Override
            public Node get() throws InterruptedException, ExecutionException {
                return process(super.get());
            }

            @Override
            public Node get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                return process(super.get(timeout, unit));
            }

            private Node process(Node n) throws ExecutionException {
                try {
                    if (n.getNodeProperties().get(MasterProvisioningNodeProperty.class) == null) {
                        n.getNodeProperties().add(mpnp.clone());
                    }
                    return n;
                } catch (IOException e) {
                    throw new ExecutionException("Error adding master provisioning node property to node " + n.getNodeName(), e);
                }
            }
        };
    }


    @Extension
    public static class DescriptorImpl extends Descriptor<Cloud> {
        public String getDisplayName() {
            return "Master Provisioning Cloud";
        }
    }
}
