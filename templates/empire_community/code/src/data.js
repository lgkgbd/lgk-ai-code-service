export const categories = [
  { id: 'court', name: '朝堂议事', desc: '国政方略 · 万机决断' },
  { id: 'military', name: '军机要闻', desc: '边关烽火 · 铁甲争鸣' },
  { id: 'culture', name: '文华雅集', desc: '翰墨丹青 · 诗酒风流' },
  { id: 'life', name: '市井百态', desc: '烟火人间 · 世情冷暖' }
]

export const topics = [
  { id: 1, title: '论河西走廊屯田戍边之策', category: 'court', author: '镇远将军', avatar: 'https://picsum.photos/seed/a1/40/40', content: '河西走廊乃中原门户，丝路咽喉。今议屯田之策，一则固边，二则惠民，三则通商。窃以为当以军屯为主、民屯为辅，沿祁连山北麓设屯田都尉，引雪水灌溉，可养兵三万之众。诸君以为如何？', replies: 23, time: '三日前', lastReply: '安西都护', liked: false, views: 341 },
  { id: 2, title: '今岁科举改制刍议——论经义与实务并重', category: 'court', author: '翰林学士', avatar: 'https://picsum.photos/seed/a2/40/40', content: '科举取士，国之根本。然今之策论多空疏，宜增实务之考。可否于经义之外，加试算学、地理、农政三科？如此则取士不拘一格，实用之才可得矣。', replies: 45, time: '五日前', lastReply: '江南布衣', liked: false, views: 567 },
  { id: 3, title: '西北烽烟再起：突厥犯边军情急报', category: 'military', author: '斥候密使', avatar: 'https://picsum.photos/seed/a3/40/40', content: '近日探得突厥诸部有异动，似欲秋高马肥之际犯我边境。甘州、凉州一线守备空虚，恳请朝廷速调朔方军五千驰援，以防不测。', replies: 67, time: '一日前', lastReply: '朔方节度使', liked: false, views: 892 },
  { id: 4, title: '水师新舰入列——海防态势之我见', category: 'military', author: '水师参将', avatar: 'https://picsum.photos/seed/a4/40/40', content: '新式艨艟战舰三艘已于昨日入列南洋水师，搭载霹雳炮十二门，可远击三百步外。然水师兵员操练未熟，窃以为当先练精兵，而后扬帆。', replies: 34, time: '二日前', lastReply: '海防观察使', liked: false, views: 456 },
  { id: 5, title: '《山河社稷图》——论当代文人画之精神', category: 'culture', author: '丹青客', avatar: 'https://picsum.photos/seed/a5/40/40', content: '文人画不在形似，而在写意。观《山河社稷图》，可见天地之广阔、山河之壮美。一笔一墨间，皆是胸中丘壑。今之画者，当以此为师。', replies: 18, time: '七日前', lastReply: '墨香居士', liked: false, views: 289 },
  { id: 6, title: '长安西市胡商云集——市舶司新政见闻', category: 'life', author: '京兆书生', avatar: 'https://picsum.photos/seed/a6/40/40', content: '近日长安西市愈加热闹，波斯、大食商贾络绎不绝。市舶司新令下，胡商税赋减半，故珍宝香料充盈市集。然坊间有言，恐利权外溢，诸君以为当如何权衡？', replies: 29, time: '四日前', lastReply: '西市令', liked: false, views: 378 },
  { id: 7, title: '秋狝大典之礼制沿革考', category: 'culture', author: '太常寺卿', avatar: 'https://picsum.photos/seed/a7/40/40', content: '秋狝之礼，古已有之。本朝沿袭前制而损益之，今岁木兰围场之典，当以简约为尚，不事铺张，以合圣朝敦朴之风。', replies: 12, time: '六日前', lastReply: '礼部侍郎', liked: false, views: 198 },
  { id: 8, title: '京城米价波动与平抑之策', category: 'life', author: '司农寺丞', avatar: 'https://picsum.photos/seed/a8/40/40', content: '今岁京畿粮价波动甚剧，秋收前米价一度涨至斗米三百钱。宜开常平仓粜米，并严查奸商囤积居奇，以安民心。', replies: 38, time: '二日前', lastReply: '京兆府尹', liked: false, views: 423 }
]

export const comments = {
  1: [
    { id: 101, author: '安西都护', avatar: 'https://picsum.photos/seed/b1/40/40', content: '屯田之策甚善，然需防吐蕃趁虚而入。窃以为当于屯田处筑堡塞，置烽燧，互为犄角。', time: '二日前', likes: 12 },
    { id: 102, author: '河西老农', avatar: 'https://picsum.photos/seed/b2/40/40', content: '祖辈居河西三代，屯田确可养民。然引水之事甚艰，祁连雪水每年五月方化，需修蓄水池以济旱时。', time: '一日前', likes: 8 }
  ],
  3: [
    { id: 301, author: '朔方节度使', avatar: 'https://picsum.photos/seed/b3/40/40', content: '军情已悉。朔方军五千精骑即日开拔，十日可抵甘州。另请旨调陇右粮草接济。', time: '半日前', likes: 24 },
    { id: 302, author: '兵部主事', avatar: 'https://picsum.photos/seed/b4/40/40', content: '已行文陇右道，调拨军粮三万石。另令河西各州坚壁清野，勿与突厥浪战。', time: '三时辰前', likes: 16 }
  ]
}
