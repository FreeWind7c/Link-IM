/**
 * BigDecimal String -> Decimal128 存量数据迁移
 *
 * 背景
 *   Spring Data MongoDB 的 spring.data.mongodb.representation.big-decimal 默认是 unspecified，
 *   即 BigDecimal 一律存成字符串。钱包余额和红包剩余金额全靠 $inc 原子加减，
 *   字符串会让服务端直接报「Cannot increment with non-numeric argument」，
 *   Criteria.gte(amount) 也会退化成字典序比较（"9" >= "10" 成立，余额校验形同虚设）。
 *
 *   application.yml 里已改成 decimal128，但那只影响「以后写进去的」。
 *   存量文档里这些字段的 BSON 类型仍是 string，不迁移的话 $inc 照样失败。
 *
 * 用法
 *   1. 先停掉 link-restapi 和 link-server-starter，避免迁移途中有新的 string 写进来
 *   2. 备份：mongodump --uri="<MONGO_URI>" --out=./dump-$(date +%F)
 *   3. mongosh "<MONGO_URI>" docs/mongo/migrate-bigdecimal-to-decimal128.js
 *   4. 看输出末尾的校验段，全部为 0 才算干净
 *   5. 再启动应用
 *
 * 特性
 *   - 幂等：只挑 $type 仍是 string 的文档改，重复跑第二次是空操作
 *   - 不吞错：转不动的值原样留着（不会静默变成 0），会在末尾校验段被列出来
 *   - 需要 MongoDB 4.0+（$convert）
 */

// ---- 待迁移的「集合.字段」清单。来源：全项目 grep "private BigDecimal" ----
const TOP_LEVEL_FIELDS = [
    ["wallet_info", "balance"],
    ["wallet_flow", "amount"],
    ["wallet_withdraw", "amount"],
    ["wallet_top_up", "amount"],
    ["wallet_refund", "amount"],
    ["red_packet", "total_amount"],
    ["red_packet", "remain_amount"],
    ["red_packet_record", "amount"],
    // 红包消息气泡里的总金额（RedPackData.amount，消息 type=1005）
    ["default_message_queue", "data.amount"],
    ["group_message_queue", "data.amount"],
];

// 数组内嵌字段（当前为空）。
// red_packet.children 已随「children 是死字段」的结论一并删除：它没有任何读取方，
// 却把这个每抢一次就 $inc 一次的热点文档撑到 ~25KB。存量文档里残留的 children 不用管，
// 没有代码再读它；真要清理是另一次 $unset 迁移，和本脚本的类型转换无关。
const ARRAY_FIELDS = [];

/** 转不动就原样返回，绝不静默改成 0 —— 宁可让校验段把它报出来人工看一眼 */
function convertExpr(path) {
    return {
        $convert: {
            input: "$" + path,
            to: "decimal",
            onError: "$" + path,
            onNull: "$" + path,
        },
    };
}

function migrateTopLevel(collName, field) {
    const coll = db.getCollection(collName);
    const filter = {};
    filter[field] = { $type: "string" };

    const before = coll.countDocuments(filter);
    if (before === 0) {
        print("  [skip] " + collName + "." + field + " — 没有 string 类型的文档");
        return 0;
    }

    const setStage = {};
    setStage[field] = convertExpr(field);
    const res = coll.updateMany(filter, [{ $set: setStage }]);

    const after = coll.countDocuments(filter);
    print("  [done] " + collName + "." + field
        + " — 待迁移 " + before + "，已改 " + res.modifiedCount + "，仍是 string " + after);
    return after;
}

function migrateArrayField(collName, arrayField, itemField) {
    const coll = db.getCollection(collName);
    const path = arrayField + "." + itemField;
    const filter = {};
    filter[path] = { $type: "string" };

    const before = coll.countDocuments(filter);
    if (before === 0) {
        print("  [skip] " + collName + "." + path + " — 没有 string 类型的文档");
        return 0;
    }

    // $map 重建整个数组：只把还是 string 的那一份转掉，其余原样保留（同一数组里可能已经混了两种类型）
    const itemPatch = {};
    itemPatch[itemField] = {
        $cond: [
            { $eq: [{ $type: "$$item." + itemField }, "string"] },
            {
                $convert: {
                    input: "$$item." + itemField,
                    to: "decimal",
                    onError: "$$item." + itemField,
                    onNull: "$$item." + itemField,
                },
            },
            "$$item." + itemField,
        ],
    };

    const setStage = {};
    setStage[arrayField] = {
        $map: {
            input: { $ifNull: ["$" + arrayField, []] },
            as: "item",
            in: { $mergeObjects: ["$$item", itemPatch] },
        },
    };

    const res = coll.updateMany(filter, [{ $set: setStage }]);
    const after = coll.countDocuments(filter);
    print("  [done] " + collName + "." + path
        + " — 待迁移 " + before + "，已改 " + res.modifiedCount + "，仍是 string " + after);
    return after;
}

// ---------------- 执行 ----------------

print("");
print("=== BigDecimal -> Decimal128 迁移开始（库：" + db.getName() + "） ===");
print("");

let remaining = 0;

print("[1/2] 顶层字段");
for (const [collName, field] of TOP_LEVEL_FIELDS) {
    remaining += migrateTopLevel(collName, field);
}

print("");
print("[2/2] 数组内嵌字段");
for (const [collName, arrayField, itemField] of ARRAY_FIELDS) {
    remaining += migrateArrayField(collName, arrayField, itemField);
}

// ---------------- 校验 ----------------

print("");
print("=== 校验：以下计数必须全为 0 ===");

for (const [collName, field] of TOP_LEVEL_FIELDS) {
    const filter = {};
    filter[field] = { $type: "string" };
    const n = db.getCollection(collName).countDocuments(filter);
    print("  " + collName + "." + field + " 仍是 string：" + n);
}
for (const [collName, arrayField, itemField] of ARRAY_FIELDS) {
    const filter = {};
    filter[arrayField + "." + itemField] = { $type: "string" };
    const n = db.getCollection(collName).countDocuments(filter);
    print("  " + collName + "." + arrayField + "." + itemField + " 仍是 string：" + n);
}

print("");
if (remaining === 0) {
    print("=== 迁移完成，可以启动应用 ===");
} else {
    print("=== 注意：还有 " + remaining + " 处没转成功 ===");
    print("这些值 $convert 解析不了（通常是空串或脏数据），已原样保留。");
    print("请人工确认后处理，不要直接启动应用 —— 它们会在 $inc 时继续报 non-numeric。");
}
print("");
