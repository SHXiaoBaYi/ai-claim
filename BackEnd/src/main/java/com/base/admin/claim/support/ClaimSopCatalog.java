package com.base.admin.claim.support;

import com.base.admin.claim.domain.ClaimSopNode;

import java.util.ArrayList;
import java.util.List;

/** 由《理赔处理流程最新版》整理的首版流程，只在库中还没有节点时写入。 */
public final class ClaimSopCatalog {

    public record Seed(String code, String parent, String type, String title, String content, String keywords, int sort) {
    }

    private ClaimSopCatalog() {
    }

    public static List<ClaimSopNode> nodes() {
        List<ClaimSopNode> nodes = new ArrayList<>();
        for (Seed seed : seeds()) {
            ClaimSopNode node = new ClaimSopNode();
            node.setNodeCode(seed.code());
            node.setParentCode(seed.parent());
            node.setNodeType(seed.type());
            node.setTitle(seed.title());
            node.setContent(seed.content());
            node.setKeywords(seed.keywords());
            node.setSortOrder(seed.sort());
            nodes.add(node);
        }
        return nodes;
    }

    private static List<Seed> seeds() {
        List<Seed> list = new ArrayList<>();
        list.add(new Seed("product", "", "CATEGORY", "产品质量", "肉品品质投诉。先问解冻和烹饪，并核对消费记录后，再按新客或老客给方案。", "", 1));
        list.add(new Seed("product.smell", "product", "SYMPTOM", "膻/腥/异味", "询问解冻方式、烹饪方式，并核对线上线下消费记录，判断是第一次无法接受，还是同款里这一件有问题。", "膻,腥,异味", 1));
        list.add(new Seed("product.smell.old", "product.smell", "ACTION", "老客", "经常吃这款、这次异常，或照片和描述能判断有质量问题：重新补发同款，或按沟通补发其他系列。品质问题同时反馈上级领导和质检部门。", "", 1));
        list.add(new Seed("product.smell.new-rare", "product.smell", "ACTION", "新客且不常吃羊肉", "第一次无法接受羊肉味道：买的是小羯羊或冰碴驹俐可换乳羔羊；本身就是乳羔羊，可补发 1 件乳羔羊或换成牛肉品尝。", "", 2));
        list.add(new Seed("product.smell.new-often", "product.smell", "ACTION", "新客但常吃羊肉", "日常吃羊肉、只是第一次吃小巴依：按原产品重新补发。", "", 3));

        list.add(new Seed("product.fat", "product", "SYMPTOM", "肥、肥瘦不均", "要整块照片和切面肥油照片。肥瘦是否超标以当前聊天里补充的质检结论为准。多数是羊排。", "肥,肥瘦,油大,太肥", 2));
        list.add(new Seed("product.fat.normal", "product.fat", "ACTION", "肥瘦比例正常", "在标准内就说明小羊是肉脂型阿勒泰大尾羊，带点脂肪更香；清炖觉得肥可以用红烧或煎烤。", "", 1));
        list.add(new Seed("product.fat.old", "product.fat", "ACTION", "老客无法接受", "经常吃羊排则这件有问题，补发同款。第一次吃羊排无法接受油脂：解释后推荐羊蝎子或羊腿，本次补发同价位其他部位，羊排建议红烧或煎烤。", "", 2));
        list.add(new Seed("product.fat.new", "product.fat", "ACTION", "新客无法接受", "先科普品种和品质。常吃别家羊排觉得肥，可赠送其他部位小包装。第一次吃羊排觉得肥，推荐羊蝎子或羊腿。", "", 3));

        list.add(new Seed("product.tough", "product", "SYMPTOM", "不好吃、口感老柴", "问清部位、纹理、烹饪方式和炖煮时间，并核对是不是经常买这个部位。", "老,柴,硬,嚼不动,咬不动,不好吃", 3));
        list.add(new Seed("product.tough.repeat", "product.tough", "ACTION", "老客且经常买这个部位", "经常买仍投诉，很大可能是这块肉本身有问题，重新赠送同部位重新品尝。", "", 1));
        list.add(new Seed("product.tough.first", "product.tough", "ACTION", "第一次买这个部位", "先看部位和烹饪是否匹配。例如牛腩、牛腿不适合炒菜，建议红烧炖煮。顾客不愿再试且情绪激动时，可赠送一块适合其做法的部位。", "", 2));
        list.add(new Seed("product.tough.wool", "product.tough", "ACTION", "每次投诉仍继续买", "消费次数和投诉次数基本相当的，按薅羊毛处理：退款同时把货品拿回，不再补发。忠实老客投诉次数明显少于购买次数的，按同部位同重量补送。", "", 3));
        list.add(new Seed("product.tough.new", "product.tough", "ACTION", "新客", "第一次体验失望但仍认可品牌的，重新赠送同部位，并交代厂区配不同批次。", "", 4));

        list.add(new Seed("product.bland", "product", "SYMPTOM", "不香或怪味", "先问清不好吃是指老硬、不香还是异味。再核对系列、是否开化、烹饪方式和生产日期。", "不香,没味,怪味,味道淡", 4));
        list.add(new Seed("product.bland.old", "product.bland", "ACTION", "老客", "吃惯正常味道、这一件明显不对的，重新补发。", "", 1));
        list.add(new Seed("product.bland.new", "product.bland", "ACTION", "新客系列不符", "若平时吃年龄较大的羊、这次是乳羔羊所以觉得淡，可换成肉香更浓的南疆乳羔羊全羊或小羯羊全羊。", "", 2));
        list.add(new Seed("product.bland.odd", "product.bland", "ACTION", "怪味", "核对运输时长和收到后是否开化又冷冻。开化后再冻导致不好吃的，补发 1 件同产品。", "", 3));

        list.add(new Seed("product.weight", "product", "SYMPTOM", "缺斤少两", "要校称视频、全部肉品块数和总重视频、纸箱和泡沫箱称重视频，并核对消费记录。", "缺斤,少两,重量不足,不够秤,分量不够", 5));
        list.add(new Seed("product.weight.fresh", "product.weight", "ACTION", "冷鲜肉或蔬菜", "积分高、消费足的，相信顾客并补发 1 件同款。积分少且消费 3 次以内的，取回原产品后补发，或退款同时取回。", "", 1));
        list.add(new Seed("product.weight.gift", "product.weight", "ACTION", "礼盒", "精品少称的可能性很低。先和物流核对发货或在途重量。确认没问题后说明无法少称；顾客坚持再走平台售后，并准备称重资料。", "", 2));
        list.add(new Seed("product.weight.confirmed", "product.weight", "ACTION", "查实确实缺斤", "按消协调一赔三，不满 500 元按 500 元。", "", 3));

        list.add(new Seed("product.color", "product", "SYMPTOM", "颜色发黑、不新鲜", "要两张自然光、无滤镜、不同角度的实物照片，并分清是物流储存还是顾客储存。", "发黑,不新鲜,变色,发暗", 6));
        list.add(new Seed("product.color.logistics", "product.color", "ACTION", "物流和储存条件正常", "货品可能有问题：道歉，并全额退款或补发。", "", 1));
        list.add(new Seed("product.color.customer", "product.color", "ACTION", "顾客储存导致", "原则上不售后。只按消费力、积分和客群关系决定是否赠送 1 件，并说明责任不在商家，后续再因储存变质不再受理。", "", 2));

        list.add(new Seed("product.foreign", "product", "SYMPTOM", "异物", "要 2 到 3 张清晰实物照片，并核对消费记录。能看清的普通异物和可疑异物处理不同。", "异物,头发,塑料,虫子,石子", 7));
        list.add(new Seed("product.foreign.no-claim", "product.foreign", "ACTION", "顾客没有索赔", "道歉后原单补发，可额外赠送赠品；或原单退款并额外赠送原单产品。", "", 1));
        list.add(new Seed("product.foreign.claim", "product.foreign", "ACTION", "顾客索赔且异物属实", "能提供消费凭证的，退款并十倍赔偿，不足 1000 元按 1000 元。可疑物品要取回核验；能明确识别的普通异物不必取回，按标准赔付。", "", 2));
        list.add(new Seed("product.foreign.fake", "product.foreign", "ACTION", "人为做假嫌疑大", "先要消费凭证，并把货品寄回公司查看后再谈赔偿。不同意寄回可以拒绝处理。", "", 3));

        list.add(new Seed("veg", "", "CATEGORY", "蔬菜质量", "按坏果数量、外箱是否破损、以及近期同品是否集中反馈来分支。", "", 2));
        list.add(new Seed("veg.bad", "veg", "SYMPTOM", "坏果", "问存放方式，看是个别坏果还是整箱较多，并要照片。", "坏果,腐烂,烂了,坏了", 1));
        list.add(new Seed("veg.bad.few", "veg.bad", "ACTION", "个别坏果", "一箱里几个坏的，按质量问题重新赠送 1 箱。只有 1 到 2 个过熟裂口或变软，解释后建议先吃；顾客不接受可补 30 元或 50 元代金券。指定哪几样不新鲜，就只理赔那几样。", "", 1));
        list.add(new Seed("veg.bad.many", "veg.bad", "ACTION", "坏果较多", "存放正常的，全部蔬菜重新补送。菜品腐烂可补发、原单退款或赠送有机蔬菜，三选一。", "", 2));
        list.add(new Seed("veg.crush", "veg", "SYMPTOM", "压坏、破损", "要不同角度照片，区分外箱破损还是箱子完好、里面压坏。", "压坏,破损,外箱,包装坏", 2));
        list.add(new Seed("veg.crush.box", "veg.crush", "ACTION", "外包装箱破损压坏", "给顾客重新补发，同时向物流发起理赔。", "", 1));
        list.add(new Seed("veg.crush.inside", "veg.crush", "ACTION", "外箱完好、里面压坏", "1 到 2 个过熟变软的解释并建议先吃，不接受可补代金券。压坏较多则重新补发。", "", 2));
        list.add(new Seed("veg.taste", "veg", "SYMPTOM", "口感不好", "问具体不好吃的原因，并看近期这款蔬菜是否已有集中反馈。", "不好吃,没味,口感", 3));
        list.add(new Seed("veg.taste.rare", "veg.taste", "ACTION", "近期没有集中反馈", "怀疑品质就道歉并重新送一份，旧菜取回品尝。更可能是口味不合时，先解释品种特点，再换其他蔬菜或肉品。", "", 1));
        list.add(new Seed("veg.taste.many", "veg.taste", "ACTION", "近期该蔬菜问题较多", "道歉，优先换成别的蔬菜，实在不行再退款。", "", 2));

        list.add(new Seed("logistics", "", "CATEGORY", "物流服务", "先核实发货时间、运输时长、当地气温、面单、开化照片和物流轨迹。", "", 3));
        list.add(new Seed("logistics.light", "logistics", "SYMPTOM", "轻微开化", "肉品表面仍冰凉、没有异味，一般不影响品质。", "轻微开化,软化,未解冻,有点化", 1));
        list.add(new Seed("logistics.light.ok", "logistics.light", "ACTION", "不影响品质", "建议先品尝，按需取用。顾客强烈不满再上报。", "", 1));
        list.add(new Seed("logistics.severe", "logistics", "SYMPTOM", "严重开化或变质", "有异味、发黏、血水多或已经温热。要核对时效是否正常，并警惕只接受退款、拒绝补发的情况。", "严重开化,变质,发黏,发粘,臭,血水", 2));
        list.add(new Seed("logistics.severe.delay", "logistics.severe", "ACTION", "时效不正常", "道歉并补发，同时向物流追责。", "", 1));
        list.add(new Seed("logistics.severe.ontime-new", "logistics.severe", "ACTION", "时效正常的电商新客", "先看平台是否有恶意退款。只要求退款、拒绝补发的按退货退款，不补发。顾客接受补发就补发 1 件，并查开化原因。", "", 2));
        list.add(new Seed("logistics.severe.ontime-old", "logistics.severe", "ACTION", "时效正常且老客没有薅羊毛记录", "道歉并补发。", "", 3));
        list.add(new Seed("logistics.lost", "logistics", "SYMPTOM", "丢失或超时未到", "查看轨迹，并向顺丰确认实际运输和预计送达。", "丢失,超时,未到,没收到,未送达", 3));
        list.add(new Seed("logistics.lost.normal", "logistics.lost", "ACTION", "物流正常或稍晚", "告知实际物流和预计送达，并说明货到后有问题可以再联系。顾客强烈不满再上报。", "", 1));
        list.add(new Seed("logistics.lost.over", "logistics.lost", "ACTION", "超过 72 小时仍未到", "加急补发 1 件同款。滞留原件由订单组联系顺丰拦截回仓或拦到点部，并多次跟签。", "", 2));

        list.add(new Seed("service", "", "CATEGORY", "服务与信息", "解释运输方式和箱体上的动检标、产地标。", "", 4));
        list.add(new Seed("service.origin", "service", "SYMPTOM", "发货地不满", "这类多是新客。先解释默认从中转站发货，时效和品质有保障。", "发货地,不是新疆,发货仓,中转", 1));
        list.add(new Seed("service.origin.unreceived", "service.origin", "ACTION", "还没收到货", "拦截原件，从新疆重新补发 1 件，并提醒以后有特殊需求可以提前说明。", "", 1));
        list.add(new Seed("service.origin.received", "service.origin", "ACTION", "已收到但非常不满", "从新疆再补发 1 件。按消费力和用途判断原件是赠送还是上门取回。", "", 2));
        return list;
    }
}
