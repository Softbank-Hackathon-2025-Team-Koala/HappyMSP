# 🚀 HappyMSP Monitor Service: Real-time Deployment Tracker

[![Tech Stack](https://img.shields.io/badge/Tech-Spring%20Boot%203.5-green)](https://github.com/sbhackathon/koala)
[![Kubernetes](https://img.shields.io/badge/K8s%20Client-21.0.0-blue)](https://github.com/kubernetes-client/java)

**Monitor Service**は、PaaSプラットフォームにおける「管制塔」として、アプリケーションの**デプロイ開始からKubernetesへのリソース生成、Pod起動、そして外部接続アドレス(Ingress/ALB)の割り当てまでの全工程をリアルタイムで追跡・可視化**するバックエンドサービスです。

単純なポーリング(Polling)にとどまらず、Kubernetes Watch APIとSSE (Server-Sent Events)を組み合わせた**ハイブリッド・モニタリング**アーキテクチャを採用し、ユーザーに遅延のないデプロイ体験を提供します。

---

## 🛠 主な機能 (Key Features)

### 1. 📡 リアルタイム・デプロイメント追跡 (Real-time Pipeline Tracking)
ユーザーがデプロイを開始した瞬間から、サービスが利用可能になるまでの全ステージを追跡します。
* **Stage 1 (Artifact Check):** AWS ECRを定期的に照会し、ビルド済みイメージの登録を確認。
* **Stage 2 (Resource Watch):** Deployment, Service, IngressなどのK8sリソース生成状況を監視。
* **Stage 3 (Pod Startup):** Podのステータス変化（Pending → ContainerCreating → Running）をミリ秒単位で検知。
* **Stage 4 (Access URL):** AWS ALB (Ingress)がプロビジョニングされ、接続アドレスが割り当てられた瞬間に通知。

### 2. 📊 ハイブリッド・ダッシュボード (Hybrid Monitoring Architecture)
正確性とパフォーマンスのバランスを考慮した**ハイブリッド方式**でリソースを監視します。
* **Event-Driven (Watch API):** K8s APIの`Watch`メカニズムを利用し、Podの生成・削除・状態変化イベントをリアルタイムで受信（低遅延）。
* **Metric Polling:** CPU/Memory使用量（`kubectl top`相当）などのメトリクス情報は、別スレッドで定期的に収集し、APIサーバーへの負荷を最小化。
* **SSE Streaming:** 収集された全データはSSEを通じてフロントエンドへプッシュ配信。

### 3. 📜 リアルタイム・ログストリーミング (Live Log Streaming)
* `kubectl logs -f` コマンドを内部プロセスで制御し、Pod内部のアプリケーションログをWebブラウザへ直接ストリーミングします。
* 非同期処理により、多数のユーザーが同時にログを閲覧してもサーバースレッドがブロックされない設計です。

### 4. 🎮 ワークロード制御 (Management Ops)
* **Pod Restart:** 異常が発生した特定のPodを強制再起動。
* **Rollout Restart:** デプロイメント全体の再起動（設定反映など）。
* **Scale In/Out:** トラフィック変動に対応するため、Podのレプリカ数（Replicas）を動的に調整。

---

## 🏗 コア・コンポーネントの役割 (Core Components)

* **Deployment Lifecycle Manager**: デプロイ・パイプライン全体のライフサイクルを管理。非同期(`@Async`)で各ステージの成功/失敗を判定し、次のステップへ進行させます。
* **K8s Resource Inspector**: Kubernetes Java Clientを使用し、クラスタ内部リソース(Deployment, Ingress等)の状態を精密に診断・検証します。
* **Real-time Dashboard Aggregator**: Watcherスレッド（イベント受信）とPollerスレッド（メトリクス収集）を並行稼働させ、最新のモニタリングデータをキャッシングおよびブロードキャストします。
* **Operation Controller**: 運用オペレーション（再起動、スケーリング、ログ閲覧）のためのAPIエンドポイントを提供し、インフラへの直接操作を仲介します。

---

## ⚙️ 技術スタック (Tech Stack)

* **Framework:** Spring Boot 3.5.7
* **Language:** Java 17
* **Cloud & Infra:** AWS (ECR, EKS, ALB), Kubernetes
* **Communication:** SSE (Server-Sent Events), REST API
