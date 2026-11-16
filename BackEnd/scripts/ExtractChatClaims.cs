using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.IO.Compression;
using System.Text;
using System.Text.RegularExpressions;
using System.Xml;

/// <summary>
/// 把「理赔投诉跟进群」消息按顾客、电话和引用串成案例，对照年度客诉表去重后写成可导入的客诉表。
/// 编译：csc /codepage:65001 /nologo /r:System.IO.Compression.dll /r:System.IO.Compression.FileSystem.dll ExtractChatClaims.cs
/// </summary>
public class ExtractChatClaims
{
    static readonly Regex PhoneRe = new Regex(@"(?<!\d)1[3-9]\d{9}(?!\d)");
    static readonly Regex NameLabelRe = new Regex(@"(?:顾客|客户|订货人|收货人|收件人|购买人|姓名|会员)\s*[:：]\s*([\u4e00-\u9fa5]{2,4})(?![\u4e00-\u9fa5])");
    static readonly Regex NameCheckRe = new Regex(@"请查收\s*([\u4e00-\u9fa5]{2,4})(?![\u4e00-\u9fa5])");
    static readonly Regex NamePhoneRe = new Regex(@"(?<![\u4e00-\u9fa5])([\u4e00-\u9fa5]{2,4})[\s:：,，、]{0,3}(?<!\d)(1[3-9]\d{9})(?!\d)");
    static readonly Regex StoreRe = new Regex(@"[\u4e00-\u9fa5A-Za-z0-9]{2,16}(?:门店|店)");
    static readonly Regex ProductLabelRe = new Regex(@"(?:货品|产品|品名|商品|单品)\s*[:：]\s*([^\n，,。；;]{2,40})");
    static readonly Regex StaffProductRe = new Regex(@"购买产品(?:名称)?(?:及数量)?\s*[:：]\s*(.+?)(?=\s+\d+[:.：]|，单号|$|\n)");
    static readonly Regex MonthDayRe = new Regex(@"(\d{1,2})\s*月\s*(\d{1,2})\s*日");
    static readonly Regex IsoDateRe = new Regex(@"\d{4}-\d{2}-\d{2}");

    static readonly string[] ClaimWords = new string[] {
        "投诉", "理赔", "赔付", "赔偿", "补发", "退款", "退货", "换货", "顾客", "客户",
        "坏了", "变质", "开化", "异味", "破损", "漏发", "少发", "发错", "不新鲜", "发臭",
        "异物", "缺斤", "丢件", "滞留", "未送达", "顺丰", "冷链", "化了", "血水", "长毛",
        "涨袋", "过期", "临期", "客诉", "小票", "开箱", "真空", "漏气", "发霉", "太肥",
        "嚼不动", "咬不动", "错发", "漏装", "货损", "拒收", "召回", "出油", "吃不动",
        "不好吃", "此单", "压坏", "短斤", "化冻", "滴水", "没收到", "未收到", "质量问题",
        "发苦", "发酸", "不香", "腥", "膻", "少称", "包装破", "货损", "化水", "坏果"
    };

    static readonly string[] StopNames = new string[] {
        "领导", "各位", "顾客", "客户", "表示", "反馈", "已经", "今天", "昨天", "照片", "视频",
        "顺丰", "客服", "理赔", "小票", "开箱", "货品", "产品", "门店", "老师", "同事", "老板",
        "销售", "投诉", "处理", "补发", "退款", "问题", "情况", "订单", "快递", "物流", "仓库",
        "质量", "服务", "行政", "财务", "采购", "肉品", "牛肉", "羊肉", "鸡肉", "蔬菜", "水果",
        "冷鲜", "冷冻", "真空", "包装", "破损", "异味", "变质", "不好", "不行", "可以", "谢谢",
        "收到", "好的", "知道", "麻烦", "查收", "联系", "沟通", "协商", "赔付", "承担", "第三方",
        "基金", "运费", "结果", "判定", "责任", "部门", "人员", "时间", "地址", "电话", "手机",
        "号码", "姓名", "订货", "收货", "购买", "下单", "店面", "这个", "那个", "一个", "我们",
        "他们", "你们",         "自己", "不是", "没有", "因为", "所以", "如果", "但是", "然后", "还是",
        "就是", "此单", "一下", "如下", "附件", "图片", "文件", "你好", "您好", "辛苦", "请看",
        "女士", "先生", "朋友", "收件", "发件", "地址", "截图", "退款", "坚持",
        "这边", "那边", "公司", "工厂", "车间", "门店", "超市", "疫情", "现在", "目前", "已经",
        "还没", "别的", "其他", "什么", "怎么", "为何", "为啥", "多少", "几个", "一点", "有点",
        "非常", "特别", "真的", "确实", "应该", "需要", "希望", "帮忙", "处理", "跟进", "回复",
        "同意", "拒绝", "申请", "通过", "完成", "结束", "开始", "继续", "马上", "尽快", "稍后"
    };

    static readonly string[] Products = new string[] {
        "冰碴驹俐", "戈壁冬羔尝鲜版", "戈壁冬羔", "冬羔", "小羯羊全羊", "乳羔羊全羊", "南疆乳羔羊", "阿勒泰大尾羊", "精品礼盒", "有机蔬菜",
        "手把肉", "羊排卷", "羊肉卷", "羊蝎子", "羊前腿", "羊后腿", "羊腿", "羊排", "羊肝", "羊肚", "羊肉串",
        "小羯羊", "乳羔羊", "驹俐", "全羊",
        "牛腩", "牛腱", "肥牛", "牛舌", "牛排", "里脊", "牛筋", "牛肋条", "腱子心", "金钱腱", "腱子",
        "鸡腿", "土鸡", "鸽子", "鸭肉", "马肠", "烤肠", "有机菜", "大盘鸡", "包子", "草莓",
        "羊肉", "牛肉", "鸡肉", "礼盒", "蔬菜", "水果"
    };

    static List<string> ProductCatalog = new List<string>();

    static readonly string[] SymptomWords = new string[] {
        "严重开化", "轻微开化", "开化", "异味", "膻味", "腥味", "变质", "不新鲜", "发臭", "发黑",
        "腐烂", "坏了", "异物", "石头", "缺斤", "少两", "太肥", "肥瘦", "发柴", "嚼不动", "咬不动",
        "太硬", "不香", "怪味", "漏发", "少发", "发错", "破损", "丢件", "超时", "未收到",
        "压坏", "涨袋", "过期", "临期", "态度"
    };

    class Msg
    {
        public DateTime Time;
        public string Sender = "";
        public string Text = "";
        public string QuoteSender = "";
        public string QuoteTime = "";
        public string QuoteText = "";
        public int QuoteIndex = -1;
        public string Id = "";
        public List<string> Phones = new List<string>();
        public List<string> Names = new List<string>();
        public bool Ack;
        public bool Claim;
    }

    class Off
    {
        public DateTime? Date;
        public string RawDate = "";
        public string Name = "";
        public string Product = "";
        public List<string> Phones = new List<string>();
    }

    class Row
    {
        public string Date = "";
        public string Purchase = "";
        public string Type = "";
        public string Store = "";
        public string Revisit = "";
        public string Name = "";
        public string Phone = "";
        public string Content = "";
        public string Category = "";
        public string Product = "";
        public string Brief = "";
        public string Opinion = "";
        public string ClaimProduct = "";
        public string Judgment = "";
        public string Third = "";
        public string Fund = "";
        public string Duty = "";
        public string Freight = "";
        public string Result = "";
        public string Admin = "";
        public string Dept = "";
        public string Handler = "";
    }

    class UF
    {
        int[] parent;
        HashSet<string>[] phones;
        HashSet<string>[] names;

        public UF(int n)
        {
            parent = new int[n];
            phones = new HashSet<string>[n];
            names = new HashSet<string>[n];
            for (int i = 0; i < n; i++)
            {
                parent[i] = i;
                phones[i] = new HashSet<string>();
                names[i] = new HashSet<string>();
            }
        }

        public int Find(int x)
        {
            if (parent[x] != x) parent[x] = Find(parent[x]);
            return parent[x];
        }

        public void AddPhone(int i, string phone) { phones[i].Add(phone); }
        public void AddName(int i, string name) { names[i].Add(name); }

        public bool Conflicts(int a, int b)
        {
            a = Find(a);
            b = Find(b);
            if (a == b) return false;
            if (Disjoint(phones[a], phones[b])) return true;
            if (Disjoint(names[a], names[b]) && !Share(phones[a], phones[b])) return true;
            return false;
        }

        public bool Union(int a, int b, bool allowConflict)
        {
            a = Find(a);
            b = Find(b);
            if (a == b) return true;
            if (!allowConflict && Disjoint(phones[a], phones[b])) return false;
            if (!allowConflict && Disjoint(names[a], names[b]) && !Share(phones[a], phones[b])) return false;
            parent[b] = a;
            phones[a].UnionWith(phones[b]);
            names[a].UnionWith(names[b]);
            return true;
        }

        static bool Disjoint(HashSet<string> a, HashSet<string> b)
        {
            if (a.Count == 0 || b.Count == 0) return false;
            foreach (string x in a) if (b.Contains(x)) return false;
            return true;
        }

        static bool Share(HashSet<string> a, HashSet<string> b)
        {
            foreach (string x in a) if (b.Contains(x)) return true;
            return false;
        }
    }

    public static int Main(string[] args)
    {
        string docs = args.Length > 0 ? args[0] : @"d:\AI-XBY\ai-claim\docs";
        try
        {
            return Run(args, docs);
        }
        catch (Exception ex)
        {
            string errorPath = @"d:\AI-XBY\ai-claim\BackEnd\scripts\chat-claim-error.txt";
            File.WriteAllText(errorPath, ex.GetType().FullName + "\n" + ex.Message + "\n" + ex.StackTrace, new UTF8Encoding(true));
            Console.WriteLine("FAILED " + ex.GetType().Name);
            return 1;
        }
    }

    static int Run(string[] args, string docs)
    {
        string chat = FindChat(docs);
        string samplePath = args.Length > 1 ? args[1] : @"d:\AI-XBY\ai-claim\BackEnd\scripts\chat-claim-sample.txt";
        string outPath = Path.Combine(docs, "群聊客诉梳理.xlsx");

        Console.WriteLine("read " + chat);
        List<Msg> msgs = ReadMessages(chat);
        if (msgs.Count < 18000) throw new Exception("messages " + msgs.Count);
        if (msgs[0].Text.IndexOf("顺丰") < 0) throw new Exception("parse failed: " + Clip(msgs[0].Text, 80));
        if (msgs[1].Text.IndexOf("13009681276") < 0) throw new Exception("phone parse failed");

        Dictionary<string, List<string>> extraPhones = ReadSensitive(chat);
        foreach (Msg msg in msgs)
        {
            List<string> more;
            if (msg.Id.Length > 0 && extraPhones.TryGetValue(msg.Id, out more))
            {
                foreach (string phone in more) AddPhone(msg, phone);
            }
        }

        HashSet<string> staff = ReadStaff(chat, msgs);
        HashSet<string> stops = new HashSet<string>(StopNames);
        foreach (Msg msg in msgs)
        {
            msg.Names = ExtractNames(msg.Text + "\n" + msg.QuoteText, staff, stops);
            msg.Ack = IsAck(msg.Text);
            msg.Claim = IsClaim(msg.Text);
        }

        HashSet<string> hotPhones = HotPhones(msgs);
        Console.WriteLine("messages=" + msgs.Count + " hotPhones=" + hotPhones.Count + " staff=" + staff.Count);

        List<Off> official = ReadOfficial(docs);
        int off2024 = 0, off2025 = 0, off2026 = 0;
        foreach (Off off in official)
        {
            if (!off.Date.HasValue) continue;
            if (off.Date.Value.Year == 2024) off2024++;
            else if (off.Date.Value.Year == 2025) off2025++;
            else if (off.Date.Value.Year == 2026) off2026++;
        }
        int shown = 0;
        foreach (Off off in official)
        {
            if (off.Date.HasValue || shown >= 8) continue;
            Console.WriteLine("undated " + off.RawDate + " / " + off.Name);
            shown++;
        }
        Console.WriteLine("official=" + official.Count + " y2024=" + off2024 + " y2025=" + off2025 + " y2026=" + off2026);
        if (off2024 < 50 || off2025 < 300 || off2026 < 100)
            throw new Exception("official cases look incomplete");
        LoadProductCatalog(official);
        Console.WriteLine("products=" + ProductCatalog.Count);

        List<List<int>> groups = Cluster(msgs, hotPhones);
        List<Row> kept = new List<Row>();
        HashSet<string> seen = new HashSet<string>();
        int noise = 0, dup = 0, splitExtra = 0;
        Dictionary<string, List<Off>> byPhone = IndexOfficial(official);
        foreach (List<int> group in groups)
        {
            List<List<int>> parts = SplitLong(msgs, group);
            if (parts.Count > 1) splitExtra += parts.Count - 1;
            foreach (List<int> part in parts)
            {
                Row row = Build(msgs, part, hotPhones, staff);
                if (row == null) { noise++; continue; }
                if (IsDup(row, official, byPhone)) { dup++; continue; }
                string key = row.Phone + "|" + row.Date + "|" + row.Name + "|" + row.Brief + "|" + Clip(row.Content, 40);
                if (!seen.Add(key)) { dup++; continue; }
                kept.Add(row);
            }
        }
        kept.Sort(delegate (Row a, Row b) { return string.CompareOrdinal(a.Date, b.Date); });
        WriteXlsx(outPath, kept);
        WriteSample(samplePath, kept, noise, dup, splitExtra, groups.Count, hotPhones);
        Console.WriteLine("groups=" + groups.Count + " kept=" + kept.Count + " noise=" + noise + " dup=" + dup + " split=" + splitExtra);
        Console.WriteLine("wrote " + outPath);
        return 0;
    }

    static string FindChat(string docs)
    {
        foreach (string file in Directory.GetFiles(docs, "*.xlsx"))
        {
            if (Path.GetFileName(file).Contains("全量消息导出")) return file;
        }
        throw new Exception("chat export not found");
    }

    static List<Msg> ReadMessages(string xlsx)
    {
        List<Dictionary<string, string>> rows = ReadFirstRows(xlsx, "消息记录");
        List<Msg> msgs = new List<Msg>();
        bool header = true;
        foreach (Dictionary<string, string> row in rows)
        {
            if (header)
            {
                header = false;
                continue;
            }
            string timeText = Cell(row, "B");
            DateTime time;
            if (!TryTime(timeText, out time)) continue;
            Msg msg = new Msg();
            msg.Time = time;
            msg.Sender = Cell(row, "C");
            msg.Text = Cell(row, "D");
            msg.QuoteSender = Cell(row, "E");
            msg.QuoteTime = Cell(row, "F");
            msg.QuoteText = Cell(row, "G");
            msg.Id = Cell(row, "J");
            foreach (Match match in PhoneRe.Matches(msg.Text + " " + msg.QuoteText + " " + Cell(row, "I")))
                AddPhone(msg, match.Value);
            msgs.Add(msg);
        }
        return msgs;
    }

    static void AddPhone(Msg msg, string phone)
    {
        if (phone == null || phone.Length != 11) return;
        if (!msg.Phones.Contains(phone)) msg.Phones.Add(phone);
    }

    static Dictionary<string, List<string>> ReadSensitive(string xlsx)
    {
        List<Dictionary<string, string>> rows = ReadFirstRows(xlsx, "敏感信息");
        Dictionary<string, List<string>> map = new Dictionary<string, List<string>>();
        if (rows.Count == 0) return map;
        Dictionary<string, string> header = rows[0];
        string idCol = FindHeader(header, "消息ID");
        string typeCol = FindHeader(header, "检出类型");
        string valueCol = FindHeader(header, "命中值");
        for (int i = 1; i < rows.Count; i++)
        {
            if (Cell(rows[i], typeCol) != "手机号") continue;
            string id = Cell(rows[i], idCol);
            string phone = DigitsPhone(Cell(rows[i], valueCol));
            if (id.Length == 0 || phone.Length != 11) continue;
            List<string> list;
            if (!map.TryGetValue(id, out list))
            {
                list = new List<string>();
                map[id] = list;
            }
            if (!list.Contains(phone)) list.Add(phone);
        }
        return map;
    }

    static HashSet<string> ReadStaff(string xlsx, List<Msg> msgs)
    {
        HashSet<string> staff = new HashSet<string>();
        Dictionary<string, int> senders = new Dictionary<string, int>();
        foreach (Msg msg in msgs)
        {
            int n;
            senders.TryGetValue(msg.Sender, out n);
            senders[msg.Sender] = n + 1;
        }
        foreach (KeyValuePair<string, int> pair in senders)
        {
            if (pair.Value >= 8) AddStaff(staff, pair.Key);
        }
        try
        {
            foreach (Dictionary<string, string> row in ReadFirstRows(xlsx, "群成员"))
            {
                foreach (string value in row.Values) AddStaff(staff, value);
            }
        }
        catch (Exception)
        {
        }
        return staff;
    }

    static void AddStaff(HashSet<string> staff, string raw)
    {
        if (raw == null) return;
        string text = raw.Replace("停用", "").Trim();
        StringBuilder chinese = new StringBuilder();
        foreach (char c in text)
        {
            if (c >= 0x4e00 && c <= 0x9fa5) chinese.Append(c);
        }
        string name = chinese.ToString();
        if (name.Length >= 2 && name.Length <= 4) staff.Add(name);
    }

    static List<string> ExtractNames(string text, HashSet<string> staff, HashSet<string> stops)
    {
        List<string> names = new List<string>();
        CollectNames(names, NameLabelRe, text, staff, stops, true);
        CollectNames(names, NameCheckRe, text, staff, stops, true);
        CollectNames(names, NamePhoneRe, text, staff, stops, false);
        return names;
    }

    static void CollectNames(List<string> names, Regex regex, string text, HashSet<string> staff, HashSet<string> stops, bool checkFollow)
    {
        foreach (Match match in regex.Matches(text ?? ""))
        {
            string name = match.Groups[1].Value;
            if (stops.Contains(name) || staff.Contains(name) || BadName(name)) continue;
            if (checkFollow && BadFollow(text, match.Groups[1].Index + name.Length)) continue;
            if (!names.Contains(name)) names.Add(name);
        }
    }

    static bool BadName(string name)
    {
        if (name == null || name.Length < 2) return true;
        string[] bits = new string[] {
            "的", "是", "给", "不", "要", "了", "在", "和", "对", "把", "让", "从", "被", "很", "就", "也", "都",
            "还", "再", "已", "这", "那", "为", "因", "退", "赔", "买", "店", "友", "补", "处", "非", "常", "持"
        };
        foreach (string bit in bits)
        {
            if (name.Contains(bit)) return true;
        }
        return false;
    }

    static bool BadFollow(string text, int end)
    {
        if (text == null || end >= text.Length) return false;
        char c = text[end];
        return c == '店' || c == '路' || c == '区' || c == '市' || c == '省' || c == '县' || c == '镇';
    }

    static bool IsAck(string text)
    {
        string cleaned = CleanMentions(text);
        if (cleaned.Length == 0) return true;
        if (cleaned == "[加密消息，无法解码]" || cleaned == "[图片]" || cleaned == "[文件]" || cleaned == "[视频]") return true;
        if (cleaned.Length <= 4 && (cleaned == "收到" || cleaned == "好的" || cleaned == "嗯" || cleaned == "ok" || cleaned == "OK" || cleaned == "1" || cleaned == "知道了" || cleaned == "谢谢" || cleaned == "辛苦"))
            return true;
        return false;
    }

    static string CleanMentions(string text)
    {
        if (text == null) return "";
        string cleaned = Regex.Replace(text, @"@[^\s@]+", " ");
        return Regex.Replace(cleaned, @"\s+", " ").Trim();
    }

    static bool IsClaim(string text)
    {
        if (string.IsNullOrEmpty(text)) return false;
        foreach (string word in ClaimWords)
        {
            if (text.Contains(word)) return true;
        }
        if (text.Contains("老") && !text.Contains("老板") && !text.Contains("老客") && !text.Contains("老师")) return true;
        if (text.Contains("硬") && (text.Contains("肉") || text.Contains("咬") || text.Contains("嚼"))) return true;
        if (text.Contains("柴")) return true;
        return false;
    }

    static HashSet<string> HotPhones(List<Msg> msgs)
    {
        Dictionary<string, HashSet<string>> nameSets = new Dictionary<string, HashSet<string>>();
        Dictionary<string, HashSet<string>> daySets = new Dictionary<string, HashSet<string>>();
        foreach (Msg msg in msgs)
        {
            foreach (string phone in msg.Phones)
            {
                HashSet<string> names;
                if (!nameSets.TryGetValue(phone, out names))
                {
                    names = new HashSet<string>();
                    nameSets[phone] = names;
                    daySets[phone] = new HashSet<string>();
                }
                foreach (string name in msg.Names) names.Add(name);
                daySets[phone].Add(msg.Time.ToString("yyyy-MM-dd"));
            }
        }
        HashSet<string> hot = new HashSet<string>();
        foreach (KeyValuePair<string, HashSet<string>> pair in nameSets)
        {
            // 同一个号码配上很多不同姓名，或从没出现姓名却跨很多天，多半是内部联系电话。
            if (pair.Value.Count >= 4 || (pair.Value.Count == 0 && daySets[pair.Key].Count >= 8))
                hot.Add(pair.Key);
        }
        return hot;
    }

    static List<List<int>> Cluster(List<Msg> msgs, HashSet<string> hotPhones)
    {
        int n = msgs.Count;
        UF uf = new UF(n);
        for (int i = 0; i < n; i++)
        {
            foreach (string phone in msgs[i].Phones)
            {
                if (!hotPhones.Contains(phone)) uf.AddPhone(i, phone);
            }
            foreach (string name in msgs[i].Names) uf.AddName(i, name);
        }

        Dictionary<string, List<int>> byKey = new Dictionary<string, List<int>>();
        Dictionary<string, List<int>> byTime = new Dictionary<string, List<int>>();
        for (int i = 0; i < n; i++)
        {
            string time = msgs[i].Time.ToString("yyyy-MM-dd HH:mm:ss");
            IndexMsg(byKey, msgs[i].Sender + "|" + time, i);
            string senderKey = SenderKey(msgs[i].Sender);
            if (senderKey.Length > 0 && senderKey != msgs[i].Sender)
                IndexMsg(byKey, senderKey + "|" + time, i);
            IndexMsg(byTime, time, i);
        }
        int quoteHits = 0;
        for (int i = 0; i < n; i++)
        {
            int quoted = FindQuoted(msgs, byKey, byTime, msgs[i]);
            if (quoted < 0) continue;
            msgs[i].QuoteIndex = quoted;
            uf.Union(i, quoted, true);
            quoteHits++;
        }
        Console.WriteLine("quoteLinks=" + quoteHits);

        Dictionary<string, List<int>> byPhone = new Dictionary<string, List<int>>();
        for (int i = 0; i < n; i++)
        {
            foreach (string phone in msgs[i].Phones)
            {
                if (hotPhones.Contains(phone)) continue;
                List<int> list;
                if (!byPhone.TryGetValue(phone, out list))
                {
                    list = new List<int>();
                    byPhone[phone] = list;
                }
                list.Add(i);
            }
        }
        foreach (List<int> list in byPhone.Values)
        {
            list.Sort(delegate (int a, int b) { return msgs[a].Time.CompareTo(msgs[b].Time); });
            for (int i = 1; i < list.Count; i++)
            {
                double days = (msgs[list[i]].Time - msgs[list[i - 1]].Time).TotalDays;
                bool sameName = ShareName(msgs[list[i]], msgs[list[i - 1]]);
                if (days <= 10 || (days <= 30 && sameName)) uf.Union(list[i], list[i - 1], false);
            }
        }

        Dictionary<string, List<int>> byName = new Dictionary<string, List<int>>();
        for (int i = 0; i < n; i++)
        {
            foreach (string name in msgs[i].Names)
            {
                List<int> list;
                if (!byName.TryGetValue(name, out list))
                {
                    list = new List<int>();
                    byName[name] = list;
                }
                list.Add(i);
            }
        }
        foreach (KeyValuePair<string, List<int>> pair in byName)
        {
            if (pair.Value.Count > 40) continue;
            List<int> list = pair.Value;
            list.Sort(delegate (int a, int b) { return msgs[a].Time.CompareTo(msgs[b].Time); });
            for (int i = 1; i < list.Count; i++)
            {
                double days = (msgs[list[i]].Time - msgs[list[i - 1]].Time).TotalDays;
                if (days <= 5) uf.Union(list[i], list[i - 1], false);
            }
        }

        for (int i = 1; i < n; i++)
        {
            double minutes = (msgs[i].Time - msgs[i - 1].Time).TotalMinutes;
            if (minutes < 0 || minutes > 30) continue;
            if (uf.Conflicts(i, i - 1)) continue;
            bool burst = msgs[i].Sender == msgs[i - 1].Sender && minutes <= 4 && msgs[i].Phones.Count == 0;
            bool ack = msgs[i].Ack && minutes <= 8;
            bool thisOrder = minutes <= 25 && HasAny(msgs[i].Text, "此单", "这条", "这个单", "该单", "上一单", "上面那个");
            bool sameQuote = msgs[i].QuoteIndex >= 0 && msgs[i].QuoteIndex == msgs[i - 1].QuoteIndex;
            if (burst || ack || thisOrder || sameQuote) uf.Union(i, i - 1, false);
        }

        Dictionary<int, List<int>> groups = new Dictionary<int, List<int>>();
        for (int i = 0; i < n; i++)
        {
            int root = uf.Find(i);
            List<int> list;
            if (!groups.TryGetValue(root, out list))
            {
                list = new List<int>();
                groups[root] = list;
            }
            list.Add(i);
        }
        return new List<List<int>>(groups.Values);
    }

    static bool DifferentPeople(Msg a, Msg b)
    {
        if (a.Phones.Count > 0 && b.Phones.Count > 0 && !SharePhone(a, b)) return true;
        if (a.Names.Count > 0 && b.Names.Count > 0 && !ShareName(a, b)) return true;
        return false;
    }

    static bool SharePhone(Msg a, Msg b)
    {
        foreach (string phone in a.Phones) if (b.Phones.Contains(phone)) return true;
        return false;
    }

    static bool ShareName(Msg a, Msg b)
    {
        foreach (string name in a.Names) if (b.Names.Contains(name)) return true;
        return false;
    }

    static List<List<int>> SplitLong(List<Msg> msgs, List<int> group)
    {
        group.Sort(delegate (int a, int b) { return msgs[a].Time.CompareTo(msgs[b].Time); });
        double span = (msgs[group[group.Count - 1]].Time - msgs[group[0]].Time).TotalDays;
        List<List<int>> parts = new List<List<int>>();
        if (span <= 21 && group.Count <= 45)
        {
            parts.Add(group);
            return parts;
        }
        List<int> cur = new List<int>();
        cur.Add(group[0]);
        for (int i = 1; i < group.Count; i++)
        {
            double gap = (msgs[group[i]].Time - msgs[group[i - 1]].Time).TotalDays;
            double curSpan = (msgs[group[i]].Time - msgs[cur[0]].Time).TotalDays;
            bool quotedHere = msgs[group[i]].QuoteIndex >= 0 && cur.Contains(msgs[group[i]].QuoteIndex);
            bool different = DifferentPeople(msgs[group[i - 1]], msgs[group[i]]);
            if (!quotedHere && ((different && gap > 0.5) || ((curSpan > 21 || cur.Count > 45) && gap > 2)))
            {
                parts.Add(cur);
                cur = new List<int>();
            }
            cur.Add(group[i]);
        }
        parts.Add(cur);
        return parts;
    }

    static Row Build(List<Msg> msgs, List<int> ids, HashSet<string> hotPhones, HashSet<string> staff)
    {
        List<Msg> substantive = new List<Msg>();
        Dictionary<string, int> phoneCount = new Dictionary<string, int>();
        Dictionary<string, int> nameCount = new Dictionary<string, int>();
        StringBuilder all = new StringBuilder();
        foreach (int id in ids)
        {
            Msg msg = msgs[id];
            if (!msg.Ack && CleanMentions(msg.Text).Length > 0)
            {
                substantive.Add(msg);
                all.Append(msg.Text).Append('\n');
                if (msg.QuoteText.Length > 0 && msg.QuoteText != "[加密消息，无法解码]" && msg.QuoteText != "[图片]" && msg.QuoteText != "[视频]")
                    all.Append(msg.QuoteText).Append('\n');
            }
            foreach (string itemPhone in msg.Phones)
            {
                if (hotPhones.Contains(itemPhone)) continue;
                int n;
                phoneCount.TryGetValue(itemPhone, out n);
                phoneCount[itemPhone] = n + 1;
            }
            foreach (string itemName in msg.Names)
            {
                int n;
                nameCount.TryGetValue(itemName, out n);
                nameCount[itemName] = n + 1;
            }
        }
        string blob = all.ToString();
        if (!IsClaim(blob)) return null;
        if (substantive.Count == 0) return null;
        if (blob.Length < 12 && phoneCount.Count == 0 && nameCount.Count == 0) return null;

        string name = Top(nameCount);
        string phone = PhoneNear(blob, name, hotPhones);
        if (phone.Length == 0) phone = Top(phoneCount);
        if (staff.Contains(name)) name = "";
        string body = StripPhones(blob, hotPhones);
        string store = BestStore(body);
        string category = CategoryOf(body);
        string product = BestProduct(body);
        if (product.Length == 0) product = InferProduct(body, category);
        if (product.Length == 0) product = category.Length > 0 ? category : "肉品";
        string symptom = SymptomOf(body);
        string brief = MakeBrief(product, category, symptom, substantive[0].Text);
        string type = TypeOf(body);
        string opinion = OpinionOf(substantive);
        string result = ResultOf(substantive);
        string claimProduct = ClaimProductOf(body);

        Row row = new Row();
        row.Date = substantive[0].Time.ToString("yyyy-MM-dd");
        row.Purchase = PurchaseOf(body, substantive[0].Time);
        row.Type = type;
        row.Store = Clip(store, 60);
        row.Revisit = body.Contains("回访") ? "是" : "";
        row.Name = Clip(name, 30);
        row.Phone = phone;
        row.Content = Compose(name, phone, store, product, substantive, hotPhones);
        row.Category = category;
        row.Product = Clip(product, 120);
        row.Brief = Clip(brief, 80);
        row.Opinion = Clip(opinion, 1500);
        row.ClaimProduct = Clip(claimProduct, 80);
        row.Judgment = JudgmentOf(type);
        row.Third = AmountAfter(body, "第三方");
        row.Fund = AmountAfter(body, "理赔基金");
        row.Duty = AmountAfter(body, "追责");
        row.Freight = AmountAfter(body, "运费");
        row.Result = Clip(result, 20);
        row.Admin = Clip(result.Length > 0 ? result : (opinion.Length > 0 ? opinion : ""), 500);
        row.Dept = DeptOf(body);
        row.Handler = HandlerOf(substantive, opinion);
        if (row.Content.Length < 8) return null;
        return row;
    }

    static string Compose(string name, string phone, string store, string product, List<Msg> substantive, HashSet<string> hotPhones)
    {
        StringBuilder text = new StringBuilder();
        if (name.Length > 0 || phone.Length > 0)
        {
            text.Append("顾客：").Append(name);
            if (phone.Length > 0) text.Append(' ').Append(phone);
            text.Append('\n');
        }
        if (store.Length > 0) text.Append("购买门店：").Append(store).Append('\n');
        if (product.Length > 0) text.Append("货品：").Append(product).Append('\n');
        string feedback = "";
        int best = -1;
        foreach (Msg msg in substantive)
        {
            int score = Score(msg.Text);
            if (score > best)
            {
                best = score;
                feedback = CleanMentions(msg.Text);
            }
        }
        if (feedback.Length > 0) text.Append("反馈：").Append(Clip(StripPhones(feedback, hotPhones), 180)).Append('\n');
        text.Append("群聊：\n");
        List<Msg> picked = PickLines(substantive);
        foreach (Msg msg in picked)
        {
            string line = CleanMentions(msg.Text);
            line = StripPhones(line, hotPhones);
            if (line.Length == 0) continue;
            text.Append(msg.Time.ToString("MM-dd HH:mm")).Append(' ').Append(msg.Sender).Append('：').Append(Clip(line, 160)).Append('\n');
            if (msg.QuoteText.Length > 0 && msg.QuoteText != "[加密消息，无法解码]")
            {
                string quoted = CleanMentions(msg.QuoteText);
                quoted = StripPhones(quoted, hotPhones);
                if (quoted.Length > 0)
                    text.Append("  引用").Append(msg.QuoteSender).Append('：').Append(Clip(quoted, 140)).Append('\n');
            }
        }
        return Clip(text.ToString().Trim(), 2500);
    }

    static int Score(string text)
    {
        if (text == null) return 0;
        int score = Math.Min(text.Length / 20, 5);
        if (PhoneRe.IsMatch(text)) score += 5;
        if (text.Contains("补发") || text.Contains("退款") || text.Contains("赔")) score += 4;
        if (IsClaim(text)) score += 3;
        if (text.Contains("顾客") || text.Contains("客户")) score += 2;
        return score;
    }

    static List<Msg> PickLines(List<Msg> substantive)
    {
        if (substantive.Count <= 18) return substantive;
        HashSet<int> keep = new HashSet<int>();
        for (int i = 0; i < substantive.Count && i < 4; i++) keep.Add(i);
        for (int i = Math.Max(0, substantive.Count - 3); i < substantive.Count; i++) keep.Add(i);
        List<int> ranked = new List<int>();
        for (int i = 0; i < substantive.Count; i++) ranked.Add(i);
        ranked.Sort(delegate (int a, int b) { return Score(substantive[b].Text).CompareTo(Score(substantive[a].Text)); });
        for (int i = 0; i < ranked.Count && keep.Count < 18; i++) keep.Add(ranked[i]);
        List<int> order = new List<int>(keep);
        order.Sort();
        List<Msg> picked = new List<Msg>();
        foreach (int i in order) picked.Add(substantive[i]);
        return picked;
    }

    static string PhoneNear(string text, string name, HashSet<string> hotPhones)
    {
        if (text == null || name == null || name.Length == 0) return "";
        int at = 0;
        while ((at = text.IndexOf(name, at)) >= 0)
        {
            int len = Math.Min(24, text.Length - at);
            Match match = PhoneRe.Match(text.Substring(at, len));
            if (match.Success && !hotPhones.Contains(match.Value)) return match.Value;
            at += name.Length;
        }
        return "";
    }

    static string Top(Dictionary<string, int> counts)
    {
        string best = "";
        int n = 0;
        foreach (KeyValuePair<string, int> pair in counts)
        {
            if (pair.Value > n) { n = pair.Value; best = pair.Key; }
        }
        return best;
    }

    static string StripPhones(string text, HashSet<string> hotPhones)
    {
        if (text == null || hotPhones.Count == 0) return text ?? "";
        foreach (string phone in hotPhones) text = text.Replace(phone, "");
        return text;
    }

    static string BestStore(string text)
    {
        Dictionary<string, int> counts = new Dictionary<string, int>();
        foreach (Match match in StoreRe.Matches(text ?? ""))
        {
            string store = match.Value;
            if (store.Contains("购买") || store.Contains("店面") || store.Contains("核实") || store.Contains("正常") || store.Length < 3) continue;
            if (store == "本店" || store == "到店" || store == "网店" || store == "分店" || store == "总店" || store == "老店" || store == "新店" || store == "门店")
                continue;
            int after = match.Index + match.Length;
            if (after < text.Length)
            {
                char next = text[after];
                if (next == '面' || next == '铺' || next == '里' || next == '员') continue;
            }
            int n;
            counts.TryGetValue(store, out n);
            counts[store] = n + 1;
        }
        return Top(counts);
    }

    static string BestProduct(string text)
    {
        if (text == null) text = "";
        Match staff = StaffProductRe.Match(text);
        if (staff.Success)
        {
            string staffValue = staff.Groups[1].Value;
            staffValue = Regex.Replace(staffValue, @"[，,].*$", "");
            staffValue = Regex.Replace(staffValue, @"^\d+件", "");
            staffValue = Regex.Replace(staffValue, @"\d+件$", "");
            staffValue = CleanProduct(staffValue);
            if (staffValue.Length >= 2) return staffValue;
        }
        Match labeled = ProductLabelRe.Match(text);
        if (labeled.Success)
        {
            string labeledValue = CleanProduct(labeled.Groups[1].Value);
            if (labeledValue.Length >= 2) return labeledValue;
        }
        Match bought = Regex.Match(text, @"(?:买的|买了|购买的|购买了)([^\n，,。；;]{2,16})");
        if (bought.Success)
        {
            string boughtValue = CleanProduct(bought.Groups[1].Value);
            string hit = CatalogHit(boughtValue);
            if (hit.Length >= 2) return hit;
            if (UsefulProduct(boughtValue)) return boughtValue;
        }
        string best = "";
        foreach (string product in ProductCatalog)
        {
            if (product.Length >= 2 && text.Contains(product) && product.Length > best.Length)
                best = product;
        }
        return best;
    }

    static string CleanProduct(string value)
    {
        if (value == null) return "";
        value = value.Trim().Trim('：', ':', '，', ',', '。');
        value = Regex.Replace(value, @"^(货品|产品|品名|商品)\s*", "");
        if (value.Length > 20) value = value.Substring(0, 20);
        if (value == "货品" || value == "产品" || value == "商品" || value == "肉" || value == "菜" || value == "一件" || value == "两件")
            return "";
        if (Regex.IsMatch(value, @"^\d") || Regex.IsMatch(value, @"^[一二两三四五六七八九十]+件$")) return "";
        return value;
    }

    static bool UsefulProduct(string name)
    {
        if (name == null) return false;
        name = name.Trim();
        if (name.Length < 2 || name.Length > 16) return false;
        if (Regex.IsMatch(name, @"^\d") || Regex.IsMatch(name, @"^[一二两三四五六七八九十百]+件$") || name == "一件" || name == "两件")
            return false;
        string[] bad = new string[] {
            "货品", "产品", "商品", "订单", "理赔", "顺丰", "基金", "运费", "顾客", "客户", "处理",
            "补发", "退款", "此单", "如果", "领导", "照片", "视频", "附件", "小票"
        };
        foreach (string item in bad) if (name == item || name.Contains(item)) return false;
        return HasAny(name, "羊", "牛", "鸡", "鸽", "鸭", "菜", "果", "肉", "肠", "排", "腿", "卷", "蝎",
            "盒", "馅", "鱼", "虾", "菌", "瓜", "椒", "萝", "包", "肝", "肚", "筋", "腱", "腩", "串",
            "驹", "羯", "羔", "礼", "饼", "饺", "锅", "肋", "里脊", "肥牛", "火锅", "草莓", "戈壁");
    }

    static string CatalogHit(string value)
    {
        foreach (string product in ProductCatalog)
        {
            if (product.Length >= 2 && value.Contains(product)) return product;
        }
        return "";
    }

    static string InferProduct(string text, string category)
    {
        if (HasAny(text, "坏果", "坏菜", "有机菜", "有机蔬菜")) return "有机蔬菜";
        if (text.Contains("马肠")) return "马肠";
        if (text.Contains("烤肠")) return "烤肠";
        if (HasAny(text, "羊排", "太肥", "油脂", "肥瘦")) return "羊排";
        if (HasAny(text, "羊蝎", "蝎子")) return "羊蝎子";
        if (HasAny(text, "乳羔")) return "乳羔羊";
        if (HasAny(text, "小羯", "羯羊")) return "小羯羊";
        if (HasAny(text, "驹俐", "冰碴")) return "冰碴驹俐";
        if (HasAny(text, "膻", "腥") && !text.Contains("牛")) return "羊肉";
        if (HasAny(text, "礼盒", "礼品")) return "礼盒";
        if (HasAny(text, "开化", "涨袋", "化冻", "血水")) return category.Length > 0 ? category : "肉品";
        if (category.Length > 0) return category;
        if (text.Contains("肉")) return "肉品";
        if (HasAny(text, "蔬菜", "青菜", "坏果", "水果")) return "蔬菜";
        return "肉品";
    }

    static void LoadProductCatalog(List<Off> official)
    {
        HashSet<string> set = new HashSet<string>();
        foreach (string product in Products) if (product.Length >= 2) set.Add(product);
        foreach (Off off in official)
        {
            string name = (off.Product ?? "").Trim();
            if (UsefulProduct(name)) set.Add(name);
            foreach (string piece in name.Split(new char[] { '、', '/', '，', ',', ' ' }, StringSplitOptions.RemoveEmptyEntries))
            {
                if (UsefulProduct(piece.Trim())) set.Add(piece.Trim());
            }
        }
        ProductCatalog = new List<string>(set);
        ProductCatalog.Sort(delegate (string a, string b) { return b.Length.CompareTo(a.Length); });
    }

    static string SenderKey(string sender)
    {
        if (sender == null) return "";
        return sender.Replace("停用", "").Replace("已离职", "").Replace("（已离职）", "").Trim();
    }

    static void IndexMsg(Dictionary<string, List<int>> map, string key, int index)
    {
        if (key == null || key.Length == 0) return;
        List<int> list;
        if (!map.TryGetValue(key, out list))
        {
            list = new List<int>();
            map[key] = list;
        }
        list.Add(index);
    }

    static int FindQuoted(List<Msg> msgs, Dictionary<string, List<int>> byKey, Dictionary<string, List<int>> byTime, Msg msg)
    {
        if (msg.QuoteSender.Length == 0 || msg.QuoteTime.Length == 0) return -1;
        DateTime quoted;
        if (!TryTime(msg.QuoteTime, out quoted)) return -1;
        string[] senders = new string[] { msg.QuoteSender, SenderKey(msg.QuoteSender) };
        for (int delta = 0; delta <= 2; delta++)
        {
            int[] offsets = delta == 0 ? new int[] { 0 } : new int[] { -delta, delta };
            foreach (int offset in offsets)
            {
                string time = quoted.AddSeconds(offset).ToString("yyyy-MM-dd HH:mm:ss");
                foreach (string sender in senders)
                {
                    if (sender.Length == 0) continue;
                    List<int> list;
                    if (!byKey.TryGetValue(sender + "|" + time, out list)) continue;
                    foreach (int i in list) if (i >= 0) return i;
                }
            }
        }
        List<int> atTime;
        if (byTime.TryGetValue(quoted.ToString("yyyy-MM-dd HH:mm:ss"), out atTime))
        {
            string want = SenderKey(msg.QuoteSender);
            foreach (int i in atTime)
            {
                string got = SenderKey(msgs[i].Sender);
                if (got == want || (want.Length >= 2 && (got.Contains(want) || want.Contains(got))))
                    return i;
            }
        }
        return -1;
    }

    static string CategoryOf(string text)
    {
        if (text == null) text = "";
        if (HasAny(text, "火锅", "烤肠", "马肠", "包子", "卤", "熟食")) return "熟食";
        bool sheep = text.Contains("羊");
        bool cow = text.Contains("牛");
        bool bird = HasAny(text, "鸡", "鸽", "鸭");
        bool veg = HasAny(text, "蔬菜", "有机菜", "水果", "黄瓜", "白菜", "西红柿", "草莓", "坏果", "坏菜");
        if (bird && !sheep && !cow) return "禽类";
        if (veg && !sheep && !cow && !text.Contains("肉")) return "蔬菜";
        if (sheep && !cow) return "羊肉";
        if (cow && !sheep) return "牛肉";
        if (text.Contains("肉")) return "肉品";
        return "";
    }

    static string SymptomOf(string text)
    {
        if (text == null) return "";
        foreach (string word in SymptomWords)
        {
            if (text.Contains(word)) return word;
        }
        if (text.Contains("柴")) return "发柴";
        string aged = text.Replace("老板", "").Replace("老客", "").Replace("老公", "").Replace("老人", "").Replace("老伴", "");
        if (aged.Contains("老")) return "肉老";
        return "";
    }

    static string MakeBrief(string product, string category, string symptom, string fallback)
    {
        string head = product.Length > 0 ? product : category;
        if (head.Length == 0) head = "商品";
        if (head.Length > 12) head = head.Substring(0, 12);
        if (symptom.Length > 0) return head + "-" + symptom;
        return head;
    }

    static string TypeOf(string text)
    {
        if (text == null) text = "";
        bool logistics = HasAny(text, "顺丰", "丢件", "破损", "开化", "超时", "未送达", "未收到", "滞留", "物流", "冷链");
        bool quality = HasAny(text, "异味", "变质", "不新鲜", "发臭", "异物", "太肥", "发柴", "嚼不动", "咬不动", "坏了", "腥", "膻");
        bool veg = CategoryOf(text) == "蔬菜";
        bool service = HasAny(text, "态度", "服务不满", "漏发短信");
        if (veg && !quality && !logistics) return "蔬菜投诉";
        if (logistics && !quality) return "物流投诉";
        if (service && !quality && !logistics) return "服务投诉";
        return "产品投诉";
    }

    static string JudgmentOf(string type)
    {
        if (type == "物流投诉") return "物流问题";
        if (type == "服务投诉") return "服务问题";
        if (type == "产品投诉" || type == "蔬菜投诉") return "质量问题";
        return "";
    }

    static string OpinionOf(List<Msg> substantive)
    {
        List<string> lines = new List<string>();
        for (int i = substantive.Count - 1; i >= 0 && lines.Count < 4; i--)
        {
            string text = CleanMentions(substantive[i].Text);
            if (!HasAny(text, "补发", "退款", "退货", "换货", "赔付", "赔偿", "承担", "无需理赔", "不予理赔", "不予处理", "无需处理", "补偿", "免单"))
                continue;
            string[] parts = Regex.Split(text, "(?<=[。！？\n])");
            for (int p = parts.Length - 1; p >= 0 && lines.Count < 4; p--)
            {
                string part = parts[p].Trim();
                if (part.Length < 4) continue;
                if (!HasAny(part, "补发", "退款", "退货", "换货", "赔付", "赔偿", "承担", "无需", "不予", "补偿", "免单")) continue;
                if (!lines.Contains(part)) lines.Add(part);
            }
        }
        lines.Reverse();
        return string.Join("", lines.ToArray());
    }

    static string ResultOf(List<Msg> substantive)
    {
        string[] keys = new string[] {
            "不予理赔", "无需理赔", "不予处理", "无需处理", "不做处理", "未做处理",
            "已补发", "已退款", "已退货", "已换货", "已赔付", "已赔偿", "处理完毕", "顾客同意", "协商一致"
        };
        for (int i = substantive.Count - 1; i >= 0; i--)
        {
            string text = substantive[i].Text ?? "";
            foreach (string key in keys)
            {
                if (text.Contains(key))
                {
                    if (key == "处理完毕") return "已处理";
                    return key;
                }
            }
        }
        return "";
    }

    static string ClaimProductOf(string text)
    {
        Match match = Regex.Match(text ?? "", @"补发\s*([^\n，,。；;]{2,30})");
        if (!match.Success) return "";
        return match.Groups[1].Value.Trim();
    }

    static string AmountAfter(string text, string label)
    {
        Match match = Regex.Match(text ?? "", label + @"[^。\n]{0,12}?(\d+(?:\.\d+)?)");
        if (!match.Success) return "";
        double value;
        if (!double.TryParse(match.Groups[1].Value, NumberStyles.Float, CultureInfo.InvariantCulture, out value)) return "";
        if (value <= 0 || value > 100000) return "";
        return match.Groups[1].Value;
    }

    static string DeptOf(string text)
    {
        Match match = Regex.Match(text ?? "", @"归属(?:责任)?(?:部门)?[:：]?\s*([\u4e00-\u9fa5]{2,10})");
        if (!match.Success) return "";
        return match.Groups[1].Value;
    }

    static string HandlerOf(List<Msg> substantive, string opinion)
    {
        if (opinion.Length == 0) return "";
        for (int i = substantive.Count - 1; i >= 0; i--)
        {
            string text = substantive[i].Text ?? "";
            if (HasAny(text, "补发", "退款", "赔付", "承担", "不予", "无需"))
            {
                string sender = (substantive[i].Sender ?? "").Replace("停用", "");
                if (sender.Length > 0 && sender.Length <= 20) return sender;
            }
        }
        return "";
    }

    static string PurchaseOf(string text, DateTime complaint)
    {
        Match iso = Regex.Match(text ?? "", @"(?:购买|下单|订货|拿货)[^0-9]{0,8}(\d{4}-\d{1,2}-\d{1,2})");
        if (iso.Success)
        {
            DateTime dt;
            if (DateTime.TryParse(iso.Groups[1].Value, out dt)) return dt.ToString("yyyy-MM-dd");
        }
        int at = IndexOfAny(text, new string[] { "购买", "下单", "订货", "拿货" });
        if (at < 0) return "";
        string window = text.Substring(at, Math.Min(24, text.Length - at));
        Match md = MonthDayRe.Match(window);
        if (!md.Success) return "";
        int month = int.Parse(md.Groups[1].Value);
        int day = int.Parse(md.Groups[2].Value);
        if (month < 1 || month > 12 || day < 1 || day > 31) return "";
        int year = complaint.Year;
        DateTime guess;
        try { guess = new DateTime(year, month, Math.Min(day, DateTime.DaysInMonth(year, month))); }
        catch (Exception) { return ""; }
        if (guess.Date > complaint.Date.AddDays(2))
        {
            try { guess = new DateTime(year - 1, month, Math.Min(day, DateTime.DaysInMonth(year - 1, month))); }
            catch (Exception) { return ""; }
        }
        return guess.ToString("yyyy-MM-dd");
    }

    static int IndexOfAny(string text, string[] words)
    {
        if (text == null) return -1;
        int best = -1;
        foreach (string word in words)
        {
            int at = text.IndexOf(word);
            if (at >= 0 && (best < 0 || at < best)) best = at;
        }
        return best;
    }

    static bool HasAny(string text, params string[] words)
    {
        if (text == null) return false;
        foreach (string word in words) if (text.Contains(word)) return true;
        return false;
    }

    static bool IsDup(Row row, List<Off> official, Dictionary<string, List<Off>> byPhone)
    {
        DateTime dt;
        if (!DateTime.TryParse(row.Date, out dt)) return false;
        if (row.Phone.Length == 11)
        {
            List<Off> list;
            if (byPhone.TryGetValue(row.Phone, out list))
            {
                foreach (Off off in list)
                {
                    if (!off.Date.HasValue)
                    {
                        if (NameHit(off.Name, row.Name)) return true;
                        continue;
                    }
                    if (Math.Abs((off.Date.Value - dt).TotalDays) <= 45) return true;
                }
            }
        }
        if (row.Name.Length >= 2)
        {
            foreach (Off off in official)
            {
                if (!NameHit(off.Name, row.Name) || !off.Date.HasValue) continue;
                if (Math.Abs((off.Date.Value - dt).TotalDays) <= 4) return true;
            }
        }
        return false;
    }

    static bool NameHit(string official, string chat)
    {
        if (official == null || chat == null || chat.Length < 2) return false;
        string name = official.Trim();
        if (name == chat) return true;
        if (!name.StartsWith(chat)) return false;
        if (name.Length == chat.Length) return true;
        char next = name[chat.Length];
        return next < 0x4e00 || next > 0x9fa5;
    }

    static Dictionary<string, List<Off>> IndexOfficial(List<Off> official)
    {
        Dictionary<string, List<Off>> map = new Dictionary<string, List<Off>>();
        foreach (Off off in official)
        {
            foreach (string phone in off.Phones)
            {
                List<Off> list;
                if (!map.TryGetValue(phone, out list))
                {
                    list = new List<Off>();
                    map[phone] = list;
                }
                list.Add(off);
            }
        }
        return map;
    }

    static List<Off> ReadOfficial(string docs)
    {
        List<Off> all = new List<Off>();
        foreach (string file in Directory.GetFiles(docs, "*.xlsx"))
        {
            string name = Path.GetFileName(file);
            if (name.Contains("全量") || name.Contains("群聊")) continue;
            string year = "";
            string sheet = "";
            if (name.Contains("2024")) { year = "2024"; sheet = FirstSheet(file); }
            else if (name.Contains("2025")) { year = "2025"; sheet = FirstSheet(file); }
            else if (name.Contains("2026")) { year = "2026"; sheet = SheetPath(file, "全年统计"); }
            else continue;
            all.AddRange(ReadOfficialSheet(file, sheet, year));
        }
        return all;
    }

    static List<Off> ReadOfficialSheet(string file, string sheetPath, string year)
    {
        List<string> shared = ReadShared(file);
        List<Dictionary<string, string>> rows = ReadSheet(file, sheetPath, shared);
        Console.WriteLine("official " + Path.GetFileName(file) + " shared=" + shared.Count + " rows=" + rows.Count);
        List<Off> list = new List<Off>();
        int headerAt = -1;
        Dictionary<string, string> header = null;
        for (int i = 0; i < rows.Count; i++)
        {
            foreach (string value in rows[i].Values)
            {
                if (value == "投诉日期") { headerAt = i; header = rows[i]; break; }
            }
            if (headerAt >= 0) break;
        }
        if (header == null) return list;
        for (int i = headerAt + 1; i < rows.Count; i++)
        {
            string content = HeaderValue(header, rows[i], "投诉内容");
            string customer = HeaderValue(header, rows[i], "客户姓名");
            if (customer.Length == 0) customer = HeaderValue(header, rows[i], "顾客");
            string product = HeaderValue(header, rows[i], "产品名称");
            string brief = HeaderValue(header, rows[i], "投诉简述");
            if (content.Length == 0 && customer.Length == 0 && product.Length == 0 && brief.Length == 0) continue;
            Off off = new Off();
            off.Name = customer;
            off.Product = product;
            off.RawDate = HeaderValue(header, rows[i], "投诉日期");
            off.Date = ParseLoose(off.RawDate, year);
            if ((year == "2024" || year == "2025") && off.Date.HasValue && off.Date.Value.Year.ToString() != year)
                off.Date = ForceFileYear(off.Date.Value, year);
            foreach (Match match in PhoneRe.Matches(customer + " " + content))
            {
                if (!off.Phones.Contains(match.Value)) off.Phones.Add(match.Value);
            }
            list.Add(off);
        }
        return list;
    }

    static string HeaderValue(Dictionary<string, string> header, Dictionary<string, string> row, string title)
    {
        foreach (KeyValuePair<string, string> pair in header)
        {
            string name = (pair.Value ?? "").Replace("\n", "").Trim();
            if (name.StartsWith(title))
            {
                string value;
                if (row.TryGetValue(pair.Key, out value) && value != null) return value.Trim();
            }
        }
        return "";
    }

    static DateTime ForceFileYear(DateTime date, string year)
    {
        int y = int.Parse(year);
        int day = date.Day;
        int max = DateTime.DaysInMonth(y, date.Month);
        if (day > max) day = max;
        return new DateTime(y, date.Month, day);
    }

    static DateTime? ParseLoose(string raw, string year)
    {
        if (raw == null) return null;
        raw = raw.Trim();
        if (raw.Length == 0) return null;
        DateTime dt;
        if (IsoDateRe.IsMatch(raw) && DateTime.TryParse(raw, out dt)) return dt.Date;
        string digits = raw.EndsWith(".0") ? raw.Substring(0, raw.Length - 2) : raw;
        int serial;
        if (int.TryParse(digits, out serial) && serial >= 40000 && serial <= 60000)
            return new DateTime(1899, 12, 30).AddDays(serial);
        Match md = MonthDayRe.Match(raw);
        int y;
        if (md.Success && int.TryParse(year, out y))
        {
            int month = int.Parse(md.Groups[1].Value);
            int day = int.Parse(md.Groups[2].Value);
            if (month >= 1 && month <= 12)
                return new DateTime(y, month, Math.Min(day, DateTime.DaysInMonth(y, month)));
        }
        return null;
    }

    static void WriteSample(string path, List<Row> kept, int noise, int dup, int splitExtra, int groups, HashSet<string> hotPhones)
    {
        Dictionary<string, int> years = new Dictionary<string, int>();
        foreach (Row row in kept)
        {
            string year = row.Date.Length >= 4 ? row.Date.Substring(0, 4) : "?";
            int n;
            years.TryGetValue(year, out n);
            years[year] = n + 1;
        }
        StringBuilder text = new StringBuilder();
        int emptyProduct = 0;
        foreach (Row row in kept) if (row.Product == null || row.Product.Length == 0) emptyProduct++;
        text.Append("kept=").Append(kept.Count).Append(" noise=").Append(noise).Append(" dup=").Append(dup)
            .Append(" groups=").Append(groups).Append(" split=").Append(splitExtra).Append(" hot=").Append(hotPhones.Count)
            .Append(" emptyProduct=").Append(emptyProduct).Append('\n');
        foreach (KeyValuePair<string, int> pair in years)
            text.Append(pair.Key).Append('=').Append(pair.Value).Append('\n');
        string[] want = new string[] { "2022", "2023", "2024", "2025", "2026" };
        foreach (string year in want)
        {
            int shown = 0;
            foreach (Row row in kept)
            {
                if (!row.Date.StartsWith(year)) continue;
                if (row.Phone.Length == 0 || row.Opinion.Length == 0) continue;
                text.Append("\n---- ").Append(year).Append(" ----\n");
                text.Append(Describe(row)).Append('\n');
                shown++;
                if (shown >= 2) break;
            }
        }
        File.WriteAllText(path, text.ToString(), new UTF8Encoding(true));
    }

    static string Describe(Row row)
    {
        return row.Date + " " + row.Name + " " + row.Phone + " " + row.Store + "\n"
            + row.Type + " / " + row.Category + " / " + row.Product + " / " + row.Brief + "\n"
            + "结果:" + row.Result + " 意见:" + Clip(row.Opinion, 180) + "\n"
            + Clip(row.Content, 700);
    }

    static void WriteXlsx(string path, List<Row> rows)
    {
        if (File.Exists(path)) File.Delete(path);
        string[] headers = new string[] {
            "投诉日期", "购买日期", "投诉类型", "购买门店", "是否回访", "客户姓名", "投诉内容",
            "产品分类", "产品名称", "投诉简述", "理赔意见", "理赔产品", "初步判定",
            "第三方承担", "理赔基金承担", "追责承担", "此单运费", "结果", "行政部处理结果",
            "归属部门", "行政处理人员"
        };
        using (ZipArchive zip = ZipFile.Open(path, ZipArchiveMode.Create))
        {
            AddText(zip, "[Content_Types].xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>" +
                "</Types>");
            AddText(zip, "_rels/.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                "</Relationships>");
            AddText(zip, "xl/_rels/workbook.xml.rels",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
                "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
                "</Relationships>");
            AddText(zip, "xl/workbook.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                "<sheets><sheet name=\"群聊梳理\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>");
            AddText(zip, "xl/styles.xml",
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" +
                "<fonts count=\"1\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font></fonts>" +
                "<fills count=\"2\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill></fills>" +
                "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>" +
                "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>" +
                "<cellXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/></cellXfs>" +
                "</styleSheet>");
            ZipArchiveEntry sheet = zip.CreateEntry("xl/worksheets/sheet1.xml", CompressionLevel.Optimal);
            using (Stream stream = sheet.Open())
            using (StreamWriter writer = new StreamWriter(stream, new UTF8Encoding(false)))
            {
                writer.Write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
                writer.Write("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>");
                WriteRow(writer, 1, headers);
                for (int i = 0; i < rows.Count; i++)
                {
                    Row row = rows[i];
                    WriteRow(writer, i + 2, new string[] {
                        row.Date, row.Purchase, row.Type, row.Store, row.Revisit, row.Name, row.Content,
                        row.Category, row.Product, row.Brief, row.Opinion, row.ClaimProduct, row.Judgment,
                        row.Third, row.Fund, row.Duty, row.Freight, row.Result, row.Admin, row.Dept, row.Handler
                    });
                }
                writer.Write("</sheetData></worksheet>");
            }
        }
    }

    static void WriteRow(StreamWriter writer, int index, string[] values)
    {
        writer.Write("<row r=\"");
        writer.Write(index);
        writer.Write("\">");
        for (int c = 0; c < values.Length; c++)
        {
            if (string.IsNullOrEmpty(values[c])) continue;
            writer.Write("<c r=\"");
            writer.Write(ColName(c));
            writer.Write(index);
            writer.Write("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">");
            writer.Write(Xml(values[c]));
            writer.Write("</t></is></c>");
        }
        writer.Write("</row>");
    }

    static string ColName(int index)
    {
        string name = "";
        index++;
        while (index > 0)
        {
            index--;
            name = (char)('A' + (index % 26)) + name;
            index /= 26;
        }
        return name;
    }

    static string Xml(string value)
    {
        StringBuilder text = new StringBuilder();
        foreach (char c in value)
        {
            if (c == '<') text.Append("&lt;");
            else if (c == '>') text.Append("&gt;");
            else if (c == '&') text.Append("&amp;");
            else if (c == '\n') text.Append("&#10;");
            else if (c == '\r' || c == '\t') text.Append(' ');
            else if (c < 32) continue;
            else text.Append(c);
        }
        return text.ToString();
    }

    static void AddText(ZipArchive zip, string name, string content)
    {
        ZipArchiveEntry entry = zip.CreateEntry(name, CompressionLevel.Optimal);
        using (Stream stream = entry.Open())
        using (StreamWriter writer = new StreamWriter(stream, new UTF8Encoding(false)))
        {
            writer.Write(content);
        }
    }

    static List<Dictionary<string, string>> ReadFirstRows(string xlsx, string sheetName)
    {
        return ReadSheet(xlsx, SheetPath(xlsx, sheetName), new List<string>());
    }

    static string FirstSheet(string xlsx)
    {
        List<string[]> sheets = WorkbookSheets(xlsx);
        if (sheets.Count == 0) throw new Exception("no sheet " + xlsx);
        return sheets[0][1];
    }

    static string SheetPath(string xlsx, string name)
    {
        foreach (string[] sheet in WorkbookSheets(xlsx))
        {
            if (sheet[0] == name) return sheet[1];
        }
        throw new Exception("missing sheet " + name);
    }

    static List<string[]> WorkbookSheets(string xlsx)
    {
        Dictionary<string, string> targets = new Dictionary<string, string>();
        using (ZipArchive zip = ZipFile.OpenRead(xlsx))
        {
            using (XmlReader reader = OpenXml(zip, "xl/_rels/workbook.xml.rels"))
            {
                while (reader.Read())
                {
                    if (reader.NodeType != XmlNodeType.Element || reader.LocalName != "Relationship") continue;
                    string id = reader.GetAttribute("Id");
                    string target = reader.GetAttribute("Target");
                    if (id == null || target == null) continue;
                    target = target.TrimStart('/');
                    if (target.StartsWith("../")) target = target.Substring(3);
                    if (!target.StartsWith("xl/")) target = "xl/" + target;
                    targets[id] = target;
                }
            }
        }
        List<string[]> sheets = new List<string[]>();
        using (ZipArchive zip = ZipFile.OpenRead(xlsx))
        using (XmlReader reader = OpenXml(zip, "xl/workbook.xml"))
        {
            while (reader.Read())
            {
                if (reader.NodeType != XmlNodeType.Element || reader.LocalName != "sheet") continue;
                string id = reader.GetAttribute("id", "http://schemas.openxmlformats.org/officeDocument/2006/relationships");
                if (string.IsNullOrEmpty(id)) id = reader.GetAttribute("r:id");
                string path;
                if (id != null && targets.TryGetValue(id, out path))
                    sheets.Add(new string[] { reader.GetAttribute("name"), path });
            }
        }
        return sheets;
    }

    static List<string> ReadShared(string xlsx)
    {
        List<string> list = new List<string>();
        using (ZipArchive zip = ZipFile.OpenRead(xlsx))
        {
            ZipArchiveEntry entry = zip.GetEntry("xl/sharedStrings.xml");
            if (entry == null) return list;
            using (XmlReader reader = XmlReader.Create(entry.Open(), XmlSettings()))
            {
                while (reader.Read())
                {
                    if (reader.NodeType != XmlNodeType.Element || reader.LocalName != "si") continue;
                    if (reader.IsEmptyElement) { list.Add(""); continue; }
                    StringBuilder text = new StringBuilder();
                    int depth = reader.Depth;
                    while (reader.Read())
                    {
                        if (reader.NodeType == XmlNodeType.EndElement && reader.LocalName == "si" && reader.Depth == depth) break;
                        if (reader.NodeType == XmlNodeType.Element && reader.LocalName == "t")
                        {
                            if (reader.IsEmptyElement) continue;
                            text.Append(reader.ReadElementContentAsString());
                            // ReadElementContentAsString 已经停在结束标签的下一个节点，紧跟着的 </si> 不能再 Read 一次跳过。
                            if (reader.NodeType == XmlNodeType.EndElement && reader.LocalName == "si" && reader.Depth == depth)
                                break;
                        }
                    }
                    list.Add(text.ToString());
                }
            }
        }
        return list;
    }

    static List<Dictionary<string, string>> ReadSheet(string xlsx, string sheetPath, List<string> shared)
    {
        List<Dictionary<string, string>> rows = new List<Dictionary<string, string>>();
        int rowStarts = 0;
        int cellStarts = 0;
        int valueNodes = 0;
        int saved = 0;
        using (ZipArchive zip = ZipFile.OpenRead(xlsx))
        using (XmlReader reader = OpenXml(zip, sheetPath))
        {
            Dictionary<string, string> row = null;
            string col = "";
            string type = "";
            bool inCell = false;
            StringBuilder buf = null;
            while (reader.Read())
            {
                if (reader.NodeType == XmlNodeType.Element && reader.LocalName == "row")
                {
                    rowStarts++;
                    row = new Dictionary<string, string>();
                    if (reader.IsEmptyElement) rows.Add(row);
                }
                else if (reader.NodeType == XmlNodeType.EndElement && reader.LocalName == "row")
                {
                    if (row != null && row.Count > 0) rows.Add(row);
                    row = null;
                }
                else if (reader.NodeType == XmlNodeType.Element && reader.LocalName == "c")
                {
                    cellStarts++;
                    string refer = reader.GetAttribute("r") ?? "";
                    col = Regex.Replace(refer, @"\d", "");
                    type = reader.GetAttribute("t") ?? "";
                    inCell = true;
                    buf = new StringBuilder();
                    if (reader.IsEmptyElement) inCell = false;
                }
                else if (reader.NodeType == XmlNodeType.EndElement && reader.LocalName == "c")
                {
                    if (inCell && row != null && col.Length > 0 && buf != null && buf.Length > 0)
                    {
                        row[col] = NormalizeSerial(buf.ToString().Trim());
                        saved++;
                    }
                    inCell = false;
                }
                else if (inCell && reader.NodeType == XmlNodeType.Element && (reader.LocalName == "v" || reader.LocalName == "t"))
                {
                    valueNodes++;
                    bool take = reader.LocalName == "t" ? type == "inlineStr" : type != "inlineStr";
                    if (!take || reader.IsEmptyElement) continue;
                    string value = reader.ReadElementContentAsString();
                    if (type == "s")
                    {
                        int index;
                        if (int.TryParse(value, out index) && index >= 0 && index < shared.Count) buf.Append(shared[index]);
                    }
                    else buf.Append(value);
                    // 同上：<v>值</v></c> 时，读完值已经停在 </c> 上。
                    if (reader.NodeType == XmlNodeType.EndElement && reader.LocalName == "c")
                    {
                        if (inCell && row != null && col.Length > 0 && buf != null && buf.Length > 0)
                        {
                            row[col] = NormalizeSerial(buf.ToString().Trim());
                            saved++;
                        }
                        inCell = false;
                    }
                }
            }
        }
        return rows;
    }

    static string NormalizeSerial(string raw)
    {
        if (raw != null && Regex.IsMatch(raw, @"^\d{5}(\.0+)?$"))
        {
            int serial = int.Parse(raw.Substring(0, 5));
            if (serial >= 40000 && serial <= 60000)
                return new DateTime(1899, 12, 30).AddDays(serial).ToString("yyyy-MM-dd");
        }
        return raw ?? "";
    }

    static XmlReader OpenXml(ZipArchive zip, string path)
    {
        ZipArchiveEntry entry = zip.GetEntry(path);
        if (entry == null) throw new Exception("missing " + path);
        return XmlReader.Create(entry.Open(), XmlSettings());
    }

    static XmlReaderSettings XmlSettings()
    {
        XmlReaderSettings settings = new XmlReaderSettings();
        settings.DtdProcessing = DtdProcessing.Prohibit;
        settings.IgnoreComments = true;
        return settings;
    }

    static string FindHeader(Dictionary<string, string> header, string title)
    {
        foreach (KeyValuePair<string, string> pair in header)
        {
            if ((pair.Value ?? "").Trim() == title) return pair.Key;
        }
        throw new Exception("missing header " + title);
    }

    static string Cell(Dictionary<string, string> row, string col)
    {
        if (row == null || col == null) return "";
        string value;
        return row.TryGetValue(col, out value) && value != null ? value.Trim() : "";
    }

    static string DigitsPhone(string raw)
    {
        if (raw == null) return "";
        Match match = PhoneRe.Match(raw);
        return match.Success ? match.Value : "";
    }

    static bool TryTime(string text, out DateTime time)
    {
        return DateTime.TryParseExact(text == null ? "" : text.Trim(), "yyyy-MM-dd HH:mm:ss", CultureInfo.InvariantCulture, DateTimeStyles.None, out time)
            || DateTime.TryParse(text, out time);
    }

    static string Clip(string value, int max)
    {
        if (value == null) return "";
        value = value.Trim();
        return value.Length <= max ? value : value.Substring(0, max);
    }
}
