# Azure Order Processing — サーバーレス注文処理パイプライン

**言語:** [English](README.md) | [日本語](README.jp.md)

---

## 概要

Java と Azure Functions を使って作った、簡単な注文処理システムです。注文が送られると、内容を確認してキューに入れ、その後バックグラウンドで処理します。実際のECサイトの裏側にあるような仕組みです。

これまでAWSを中心に使っていたので、Azureのサーバーレスサービスを実際に手を動かして学ぶために作りました。

## 仕組み

![アーキテクチャ図](architecture-diagram.png)

1. クライアントが注文情報（顧客情報・商品リスト）をHTTPで送信
2. `SubmitOrder` 関数が注文内容を確認し、合計金額を計算して、Service Busのキューに送る
3. `ProcessOrder` 関数がキューから注文を受け取り、処理済みにして、JSONファイルとしてBlob Storageに保存

2つの関数は直接つながっておらず、キューを通してつながっています。そのため、注文はすぐに受け付けられ、実際の処理は別でバックグラウンドで行われます。

## 使用技術

- Java 17
- Azure Functions(HTTPトリガー + Service Busトリガー)
- Azure Service Bus(キュー)
- Azure Blob Storage
- Maven
- リソース作成はAzure CLIで実施

## 作業の様子(スクリーンショット)

**ローカルでFunctionsを実行**
![Functions running](1-functions-running.png)

**注文の送信**
![API request](2-api-request.png)

**注文がパイプラインを通る様子(キュー→処理)**
![Pipeline execution](3-pipeline-execution.png)

**Blob Storageに保存された注文ファイル**
![Blob storage](4-blob-storage.png)

**Azure上のリソース**
![Azure resources](5-azure-resources.png)

## 実行方法

Azureリソースの作成:

```bash
az group create --name rg-order-processing --location japaneast

az storage account create --name <storage-name> \
  --resource-group rg-order-processing --location japaneast --sku Standard_LRS

az servicebus namespace create --name <servicebus-name> \
  --resource-group rg-order-processing --location japaneast --sku Basic

az servicebus queue create --name orders-queue \
  --namespace-name <servicebus-name> --resource-group rg-order-processing

az storage container create --name orders \
  --account-name <storage-name> --account-key <key>
```

`local.settings.json.example` を `local.settings.json` にコピーして、接続文字列を入力してください。

実行:

```bash
mvn clean package
mvn azure-functions:run
```

テスト:

```bash
curl -X POST http://localhost:7071/api/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerName": "Tanaka Taro",
    "customerEmail": "tanaka@example.com",
    "items": [
      {"productName": "Laptop", "quantity": 1, "price": 150000},
      {"productName": "Mouse", "quantity": 2, "price": 2500}
    ]
  }'
```

## 設計について

- 合計金額はサーバー側で計算しています。クライアントから送られた金額をそのまま信用すると、不正な金額を送られる可能性があるためです
- 同時に複数のリクエストが来ても値が上書きされないように、データベースの更新はアトミックな操作で行うのが基本です(このプロジェクトでは直接使っていませんが、同じ考え方で設計しています)
- キューを使うことで「注文を受け付ける」処理と「注文を処理する」処理を分離しています。処理に時間がかかったり失敗しても、顧客側のリクエストには影響しません

## その他

- Azureの無料枠内で動作確認しました
- 動作確認後はリソースを削除し、課金が発生しないようにしています

## 作成者

Krishnaraj Ramachandran — [GitHub](https://github.com/krishfemto)
