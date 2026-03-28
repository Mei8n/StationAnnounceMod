# StationAnnounceMod
[![MCVer](https://img.shields.io/badge/Minecraft-1.7.10-brightgreen)](https://www.minecraft.net/)
[![ForgeVer](https://img.shields.io/badge/Forge-10.13.4.1614-important)](https://files.minecraftforge.net/maven/net/minecraftforge/forge/index_1.7.10.html)
[![DLCount](https://img.shields.io/github/downloads/Mei8n/stationannouncemod/total)](https://github.com/Mei8n/stationannouncemod/releases)
[![DLCountLatest](https://img.shields.io/github/downloads/Mei8n/stationannouncemod/latest/total)](https://github.com/Mei8n/stationannouncemod/releases/latest)
[![LatestRelease](https://img.shields.io/github/v/release/Mei8n/stationannouncemod)](https://github.com/Mei8n/stationannouncemod/releases/latest)
[![LatestPreRelease](https://img.shields.io/github/v/release/Mei8n/stationannouncemod?include_prereleases)](https://github.com/Mei8n/stationannouncemod/releases)

Minecraftで駅放送を流せるようにするmodです。\
現実の駅放送と同じく、パーツ単位で音声を用意しそれを組み合わせることができます。\
また、RealTrainModと連携して詳細放送も流すことができます。

## 注意事項
**当modはベータ版です。**\
当modの導入によって生じた損害について、私は一切責任を負いません。\
予告なしに破壊的変更が加えられることがあります。\
ワールドデータの破損等が起こる可能性があるので使用前に必ずセーブデータのバックアップを取ってください。

## Download

Latest Release [Download](https://github.com/Mei8n/stationannouncemod/releases/latest)\
Latest Pre-Release [Download](https://github.com/Mei8n/stationannouncemod/releases)

## 追加ブロック、アイテムの説明
### ・放送装置ブロック
親となるブロックです。レッドストーン入力で動作します。\
設定画面でjsを読み込むことができます。\
Link Keyフィールドに"リンクキー"を登録することで、後述のブロックとの連携が可能です。

### ・スピーカー
音声が流れるブロックです。\
連携したい放送装置ブロックと同じリンクキーを登録して使用します。\
可聴範囲と音量を設定することができます。\
また、スニ―クしながらスポイトすることで登録した情報を保持したアイテムをスポイトすることができます。

### ・放送開始ブロック
連携したい放送装置ブロックと同じリンクキーを登録して使用します。\
レッドストーンまたはRTMの車両検知で放送再生を開始します。

### ・放送停止ブロック
連携したい放送装置ブロックと同じリンクキーを登録して使用します。\
レッドストーンまたはRTMの車両検知で再生されている放送を停止します。

## RealTrainModを導入していると追加されるブロックの説明
### ・列車選別装置
連携したい放送装置ブロックと同じリンクキーを登録して使用します。\
RTMの車両側スクリプトで登録したdataMapの読み取りと放送装置ブロックを行います。\
Add Keyを押すと1つ入力フィールドが挿入されます。\
Key NameにdataMapの名前を登録し、Typeで適切な型を指定します。\
読み取ったdataMapは放送スクリプト側で利用できるようになります。

## 放送パーツ、リストの登録方法
[こちら](howtoAddAnnounce.md) をご覧ください
