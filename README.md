# Sparkle - 花火

一个使用 Java 语言的 BTN 实现与集成的 BitTorrent Tracker。

> [!WARNING]
> Sparkle 目前处于实验性状态，不建议部署生产环境使用

## 日程表

目前仍有部分功能缺失，正在努力完成

* [ ] 高频操作内存缓存，目前仍在直接查询 PostgreSQL 数据库
* [ ] 前端页面
* [ ] 高级检索功能
* ...

## 简述

Sparkle 是一个遵循 [BTN 规范](https://github.com/PBH-BTN/BTN-Spec) 的官方 Java 实现。能够接收 PBH 等 BTN 兼容客户端的数据上报，并下发云规则。除此之外，Sparkle 也是首个使用 Java 语言的支持 Scrape 和紧凑压缩格式 Peers 响应的 Bittorrent Tracker。

## 需要环境

* Java 21 或者更高版本
* PostgreSQL
* Redis
* Github OAuth Application

## 部署

目前 Sparkle 仍处于早期开发阶段，我们暂时不提供部署教程。
