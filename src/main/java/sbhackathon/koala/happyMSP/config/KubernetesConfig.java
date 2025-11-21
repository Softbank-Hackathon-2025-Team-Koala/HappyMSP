package sbhackathon.koala.happyMSP.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "k8s")
public class KubernetesConfig {

    private String namespace = "default";
    private ImagePullSecret imagePullSecret = new ImagePullSecret();

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public ImagePullSecret getImagePullSecret() {
        return imagePullSecret;
    }

    public void setImagePullSecret(ImagePullSecret imagePullSecret) {
        this.imagePullSecret = imagePullSecret;
    }

    public static class ImagePullSecret {
        private String name = "ecr-secret";
        private boolean autoCreate = true;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isAutoCreate() {
            return autoCreate;
        }

        public void setAutoCreate(boolean autoCreate) {
            this.autoCreate = autoCreate;
        }
    }
}

