# See here for image contents: https://github.com/microsoft/vscode-dev-containers/tree/v0.195.0/containers/java/.devcontainer/base.Dockerfile
# [Choice] Java version (use -bullseye variants on local arm64/Apple Silicon): 8, 11, 16, 8-bullseye, 11-bullseye, 16-bullseye, 8-buster, 11-buster, 16-buster
ARG VARIANT=11-bullseye
FROM mcr.microsoft.com/vscode/devcontainers/java:0-${VARIANT}

# [Option] Install Maven
ARG INSTALL_MAVEN="false"
ARG MAVEN_VERSION=""
# [Option] Install Gradle
ARG INSTALL_GRADLE="false"
ARG GRADLE_VERSION=""
RUN if [ "${INSTALL_MAVEN}" = "true" ]; then su vscode -c "umask 0002 && . /usr/local/sdkman/bin/sdkman-init.sh && sdk install maven \"${MAVEN_VERSION}\""; fi \
    && if [ "${INSTALL_GRADLE}" = "true" ]; then su vscode -c "umask 0002 && . /usr/local/sdkman/bin/sdkman-init.sh && sdk install gradle \"${GRADLE_VERSION}\""; fi

# [Choice] Node.js version: none, lts/*, 16, 14, 12, 10
ARG NODE_VERSION="none"
RUN if [ "${NODE_VERSION}" != "none" ]; then su vscode -c "umask 0002 && . /usr/local/share/nvm/nvm.sh && nvm install ${NODE_VERSION} 2>&1"; fi

# = = = = = = = = = = = = = = = = = = = = = = = =
# Install misc tools for development purposes
# = = = = = = = = = = = = = = = = = = = = = = = =
RUN apt-get update && apt-get upgrade -y && \
    apt-get -y install mlocate && \
    apt-get install tree -y && \
    apt-get install make -y && \
    apt-get install dos2unix -y

# = = = = = = = = = = = = = = = = = = = = = = = =
# Docker
# = = = = = = = = = = = = = = = = = = = = = = = =

# Docker Compose version
ARG COMPOSE_VERSION=1.24.0

# Install Docker CE CLI
RUN apt-get install -y apt-transport-https ca-certificates curl gnupg-agent software-properties-common lsb-release \
    && curl -fsSL https://download.docker.com/linux/$(lsb_release -is | tr '[:upper:]' '[:lower:]')/gpg | (OUT=$(apt-key add - 2>&1) || echo $OUT) \
    && add-apt-repository "deb [arch=amd64] https://download.docker.com/linux/$(lsb_release -is | tr '[:upper:]' '[:lower:]') $(lsb_release -cs) stable" \
    && apt-get update \
    && apt-get install -y docker-ce-cli

# Install Docker Compose
RUN curl -sSL "https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose \
    && chmod +x /usr/local/bin/docker-compose

# = = = = = = = = = = = = = = = = = = = = = = = =
# Kubernetes
# = = = = = = = = = = = = = = = = = = = = = = = =
# Helm Version
ARG HELM_VERSION=3.6.3

# Install Kubectl
RUN echo "deb https://apt.kubernetes.io/ kubernetes-xenial main" > /etc/apt/sources.list.d/kubernetes.list \
    && curl -sL https://packages.cloud.google.com/apt/doc/apt-key.gpg | apt-key add - 2>/dev/null \
    && apt-get update \
    && apt-get install -y kubectl

# Install Helm
RUN mkdir -p /tmp/downloads/helm \
    && curl -sL -o /tmp/downloads/helm.tar.gz https://get.helm.sh/helm-v${HELM_VERSION}-linux-amd64.tar.gz \
    && tar -C /tmp/downloads/helm -zxvf /tmp/downloads/helm.tar.gz \
    && mv /tmp/downloads/helm/linux-amd64/helm /usr/local/bin

# Make kubectl completions work with 'k' alias
RUN echo 'alias k=kubectl' >> "/root/.zshrc" \
    && echo 'complete -F __start_kubectl k' >> "/root/.zshrc" \
    && echo "[[ $commands[kubectl] ]] && source <(kubectl completion zsh)" >> "/root/.zshrc"

RUN echo 'alias k=kubectl' >> "/root/.bashrc" \
    && echo 'complete -F __start_kubectl k' >> "/root/.bashrc" \
    && echo "[[ $commands[kubectl] ]] && source <(kubectl completion zsh)" >> "/root/.bashrc"

# Install kubectx
ENV VERSION "0.9.4"
RUN echo "Installing kubectx" \
    && curl -L -o /usr/local/bin/kubectx https://raw.githubusercontent.com/ahmetb/kubectx/v${VERSION}/kubectx \
	&& curl -L -o /usr/local/bin/kubens https://raw.githubusercontent.com/ahmetb/kubectx/v${VERSION}/kubens \
    && chmod +x /usr/local/bin/kubectx \
    && chmod +x /usr/local/bin/kubens