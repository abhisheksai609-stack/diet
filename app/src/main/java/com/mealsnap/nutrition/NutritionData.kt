package com.mealsnap.nutrition

/**
 * Built-in nutrition table, values per 100 g of the food as normally eaten (cooked, prepared).
 * Figures are rounded averages in the spirit of public food-composition tables (USDA and similar);
 * real dishes vary by recipe, so treat every number as an estimate.
 *
 * Columns: name | aliases (comma separated) | kcal | protein g | carbs g | fat g | fibre g |
 *          sugar g | sodium mg | typical serving g | serving description
 *
 * A line starting with `!` marks a generic fallback entry (used when nothing specific matches).
 */
internal object NutritionData {
    const val RAW = """
White bread|bread,toast,sliced bread,white toast|265|9|49|3.2|2.7|5|491|60|2 slices
Whole wheat bread|brown bread,wholemeal bread,multigrain bread|247|13|41|3.4|7|6|450|60|2 slices
Bagel|bagels|257|10|50|1.6|2.3|6|439|105|1 bagel
Croissant|croissants,butter croissant|406|8.2|45.8|21|2.6|11|400|60|1 croissant
Pancakes|pancake,hotcake,flapjack,crepe|227|6.4|28|9.7|1.1|7|439|150|3 pancakes
Waffle|waffles,belgian waffle|291|7.9|33|14.1|1.8|5|511|75|1 waffle
French toast|french toasts|229|7.7|25|11|1|8|479|130|2 slices
Omelette|omelet,cheese omelette,egg omelette|154|10.6|0.6|12|0|0.4|312|120|2-egg omelette
Scrambled eggs|scrambled egg,egg bhurji,anda bhurji|149|10|1.6|11|0|1.4|145|120|2 eggs
Boiled egg|hard boiled egg,eggs,egg,soft boiled egg,boiled eggs|155|12.6|1.1|10.6|0|1.1|124|100|2 eggs
Fried egg|sunny side up,fried eggs,egg fry|196|13.6|0.8|15|0|0.4|207|50|1 egg
Avocado toast|avocado on toast|190|5|20|10|5|2|280|140|1 slice
Oatmeal|porridge,oats,oat porridge|71|2.5|12|1.5|1.7|0.3|49|240|1 bowl
Granola|muesli,granola bowl|471|10|64|20|7|24|26|50|half a cup
Cereal with milk|cornflakes,breakfast cereal,corn flakes|105|3.4|19|1.8|1|7|110|250|1 bowl
Yogurt|yoghurt,curd,dahi,plain yogurt|61|3.5|4.7|3.3|0|4.7|46|170|1 cup
Greek yogurt|greek yoghurt,strained yogurt|97|9|3.9|5|0|3.6|35|170|1 cup
Smoothie|fruit smoothie,banana smoothie,berry smoothie|62|1.2|13|0.5|1.2|11|10|350|1 glass
Muffin|blueberry muffin,chocolate muffin|377|5.4|54|16|1.7|33|480|115|1 muffin
Donut|doughnut,donuts,glazed donut|452|4.9|51|25|1.7|23|326|60|1 donut
Cupcake|cup cake,cupcakes|305|3.5|45|13|0.9|32|243|75|1 cupcake
Cake|chocolate cake,sponge cake,birthday cake,layer cake,gateau,torte|371|5|53|16|2|35|300|100|1 slice
Cheesecake|cheese cake|321|5.5|26|23|0.4|22|257|120|1 slice
Brownie|brownies,chocolate brownie|466|6|50|27|2.5|36|240|60|1 brownie
Cookie|cookies,chocolate chip cookie,biscuit,biscuits|488|5|64|24|2.4|36|380|30|2 cookies
Apple pie|pie,fruit pie,pumpkin pie,cherry pie,tart|237|1.9|34|11|1.6|15|266|125|1 slice
Ice cream|icecream,gelato,frozen yogurt,sundae,sorbet|207|3.5|24|11|0.7|21|80|100|2 scoops
Chocolate|chocolate bar,dark chocolate,milk chocolate,candy|546|5|61|31|7|48|24|40|1 small bar
Tiramisu|tiramisu cake|283|4.5|30|16|0.5|22|100|120|1 slice
Pudding|custard,flan,creme brulee,panna cotta,mousse|130|3.5|20|4|0|17|100|130|1 cup
Hamburger|burger,beef burger,hamburgers|254|13|30|9|1.2|6|480|180|1 burger
Cheeseburger|cheese burger,double cheeseburger|303|15|29|14|1.3|6|600|190|1 burger
Veggie burger|vegetarian burger,plant burger,bean burger|220|9|27|8|4|4|450|170|1 burger
Chicken burger|chicken sandwich,crispy chicken burger,zinger burger|260|14|27|10|1.5|5|520|190|1 burger
Hot dog|hotdog,frankfurter,corn dog|290|10.4|23|17|1|4|810|120|1 hot dog
French fries|fries,chips,finger chips,potato fries|312|3.4|41|15|3.8|0.3|210|117|1 medium portion
Chicken nuggets|nuggets,chicken nugget,popcorn chicken|296|15|18|18|1|0.5|560|100|6 nuggets
Fried chicken|chicken fry,crispy chicken,southern fried chicken|246|19|10|15|0.4|0|480|150|2 pieces
Chicken wings|buffalo wings,wings,hot wings|254|22|2|17|0.2|0.5|700|150|6 wings
Pizza|cheese pizza,margherita pizza,margherita,pizza slice,veg pizza|266|11|33|10|2.3|3.6|598|107|1 slice
Pepperoni pizza|pepperoni,meat pizza,sausage pizza,non veg pizza|298|12|31|14|2|3|760|110|1 slice
Sandwich|sandwiches,sub,submarine,veg sandwich,panini|250|12|28|9|2|4|600|200|1 sandwich
Club sandwich|club|232|13|20|11|1.5|3|650|250|1 sandwich
Grilled cheese|grilled cheese sandwich,cheese toast,toastie|330|12|28|19|1.3|4|700|150|1 sandwich
Burrito|burritos,burrito bowl,chicken burrito|206|8.5|25|8|3|1.5|470|250|1 burrito
Taco|tacos,street taco,taco shell|226|9|20|12|3|1.5|397|170|2 tacos
Quesadilla|quesadillas|290|12|25|16|1.5|2|620|180|1 quesadilla
Nachos|nacho,tortilla chips with cheese|346|8|36|19|4|2|500|150|1 plate
Wrap|chicken wrap,veg wrap,tortilla wrap,kathi roll,frankie|220|11|25|8|2|2|520|200|1 wrap
Shawarma|gyro,doner,kebab roll,shawarma wrap|215|14|18|10|1.5|2|520|250|1 wrap
Kebab|kebabs,seekh kebab,shish kebab,tikka kebab,galouti|230|20|3|15|0.5|1|520|120|3 pieces
Garden salad|salad,green salad,mixed salad,tossed salad,veg salad,vegetable salad|45|1.5|6|2|2|3|60|200|1 bowl with dressing
Caesar salad|caesar|190|6|8|15|1.5|2|480|250|1 bowl
Greek salad|feta salad|110|3|7|8|2|4|330|250|1 bowl
Potato salad|macaroni salad,pasta salad,egg salad,russian salad|143|2.5|15|8|1.2|3|300|150|1 portion
Coleslaw|slaw|150|1|15|10|2|12|240|100|1 side
Fruit salad|fruit bowl,mixed fruit|50|0.6|12|0.2|1.2|10|3|200|1 bowl
Caprese salad|caprese|180|10|3|14|1|2|300|150|1 plate
Tuna salad|tuna|190|15|5|12|1|2|400|180|1 serving
Chicken salad|grilled chicken salad|150|14|5|8|1|2|350|250|1 bowl
White rice|steamed rice,boiled rice,rice,plain rice,basmati rice,jeera rice|130|2.7|28|0.3|0.4|0|1|180|1 cup cooked
Brown rice|brown rice bowl|123|2.7|26|1|1.8|0.4|4|180|1 cup cooked
Fried rice|egg fried rice,chicken fried rice,veg fried rice,schezwan rice|163|4.5|22|6.5|1|1.5|450|250|1 plate
Biryani|chicken biryani,mutton biryani,veg biryani,hyderabadi biryani,dum biryani,egg biryani|170|8|21|6|1|1|420|300|1 plate
Pulao|pilaf,pilau,veg pulao,vegetable rice,peas pulao|150|3.3|25|4|1|1|300|220|1 plate
Khichdi|khichri,dal khichdi,moong dal khichdi|110|4|18|2.5|2|1|250|250|1 bowl
Curd rice|thayir sadam,yogurt rice|120|3.5|19|3.5|0.5|2|200|250|1 bowl
Lemon rice|tamarind rice,tomato rice,puliyogare,coconut rice|160|3|27|4.5|1|1|300|220|1 plate
Pasta|spaghetti,penne,macaroni,fusilli,plain pasta|158|5.8|31|0.9|1.8|0.6|1|200|1 plate cooked
Spaghetti bolognese|bolognese,spaghetti meat sauce,pasta bolognese|150|7|19|5|1.8|3|330|300|1 plate
Pasta in tomato sauce|marinara,penne arrabbiata,pomodoro,red sauce pasta,pasta arrabiata|125|4|22|2.5|2|4|300|300|1 plate
Carbonara|spaghetti carbonara,pasta carbonara|190|8|20|8|1|1|370|300|1 plate
Fettuccine alfredo|alfredo,white sauce pasta,creamy pasta,pasta alfredo|210|7|22|10|1|2|400|300|1 plate
Mac and cheese|macaroni and cheese,mac n cheese|164|7|18|7|0.8|3|470|250|1 bowl
Lasagna|lasagne|135|8|13|5.6|1.1|3.5|400|300|1 piece
Ravioli|tortellini,gnocchi,stuffed pasta|170|7|24|5|2|2|450|250|1 plate
Ramen|ramen noodles,noodle soup,udon,japanese noodles,soba|100|5|13|3|0.7|1|420|500|1 bowl
Pho|vietnamese noodle soup,beef noodle soup|65|4|8|1.5|0.5|1|350|500|1 bowl
Pad thai|thai noodles|170|7|22|6|1.5|5|460|300|1 plate
Chow mein|lo mein,hakka noodles,veg noodles,chinese noodles,stir fried noodles,schezwan noodles,maggi|160|5|22|6|1.5|2|500|250|1 plate
Spring rolls|spring roll,egg roll,veg roll|230|5|28|11|2|3|500|100|2 rolls
Dumplings|gyoza,momos,momo,dim sum,potsticker,wonton,siomai|200|8|24|7|1.5|1.5|450|150|6 pieces
Sushi|sushi roll,maki,nigiri,california roll|145|6|25|2|0.8|4|380|180|8 pieces
Sashimi|raw fish,tuna sashimi,salmon sashimi|130|22|0|4.5|0|0|60|100|5 slices
Tempura|fried prawns,battered fish|260|7|25|15|1|1|300|120|1 serving
Teriyaki chicken|chicken teriyaki,teriyaki|180|17|12|7|0.3|9|700|250|1 serving
Roti|chapati,phulka,chapatti,tortilla roti,whole wheat roti|297|9.6|50|7|7|1|250|80|2 rotis
Naan|garlic naan,butter naan,kulcha,tandoori roti|290|9|50|6|2|4|500|90|1 naan
Paratha|aloo paratha,parantha,stuffed paratha,gobi paratha,paneer paratha|290|6|40|12|3|1|400|110|1 paratha
Puri|poori,bhatura,luchi,kachori|380|7|45|19|2|1|300|75|3 puris
Dosa|plain dosa,rava dosa,set dosa,paper dosa|168|4|27|4.5|1.5|1|250|100|1 dosa
Masala dosa|mysore masala dosa|200|5|29|7|2|1.5|380|200|1 dosa
Idli|idly,rava idli,steamed rice cake|135|4|28|0.5|1|0.5|200|120|3 idlis
Vada|medu vada,vadai,dahi vada,sambar vada|260|8|25|14|3|1|350|80|2 vadas
Uttapam|uthappam,oothappam|170|5|27|4.5|2|2|300|150|1 uttapam
Sambar|sambhar,lentil vegetable stew|50|2.5|7|1.5|1.8|2|300|200|1 bowl
Rasam|pepper water,tomato rasam|25|1|4|0.5|0.5|1|300|200|1 cup
Chutney|coconut chutney,green chutney,mint chutney,tomato chutney|150|2|6|13|3|2|200|40|2 tbsp
Dal|dal tadka,lentil curry,dal fry,dhal,toor dal,moong dal,lentils,daal|105|6|15|2.5|4|1.5|300|200|1 bowl
Dal makhani|black lentil curry,dal makhni|140|6|14|7|3.5|2|320|200|1 bowl
Chana masala|chole,chickpea curry,chhole,chole masala,chana|150|7|20|5|5|3|350|200|1 bowl
Rajma|rajma masala,kidney bean curry,red kidney beans|130|6|18|3.5|5|2|330|200|1 bowl
Paneer butter masala|paneer makhani,shahi paneer,kadai paneer,paneer curry,matar paneer|190|8|8|14|1|4|400|200|1 bowl
Palak paneer|spinach paneer,saag paneer,sarson ka saag,saag|150|7|6|11|2|2|350|200|1 bowl
Paneer tikka|tandoori paneer,paneer kebab|230|15|6|16|1|2|500|120|5 pieces
Butter chicken|murgh makhani,chicken makhani|160|12|6|10|1|3|420|250|1 bowl
Chicken tikka masala|tikka masala,chicken masala,kadai chicken,chicken korma,chicken gravy|150|12|7|8|1|3|400|250|1 bowl
Chicken curry|indian chicken curry,kari,chicken tikka curry,thai curry,green curry,red curry|140|13|5|8|1|2|400|250|1 bowl
Tandoori chicken|chicken tikka,tandoori,grilled chicken tikka,chicken 65|190|27|3|8|0.5|1|400|150|4 pieces
Mutton curry|lamb curry,goat curry,rogan josh,keema,mutton masala,beef curry|170|13|5|11|1|2|400|200|1 bowl
Fish curry|fish masala,goan fish curry,prawn curry,shrimp curry|120|13|4|6|1|2|380|200|1 bowl
Egg curry|anda curry,egg masala|140|8|6|9|1.5|2|380|200|1 bowl
Aloo gobi|potato cauliflower curry,gobi masala|90|2.5|11|4|3|2|300|200|1 bowl
Potato curry|aloo sabzi,aloo masala,aloo jeera,dum aloo,aloo curry|100|2|14|4|2|2|300|200|1 bowl
Vegetable curry|mixed veg curry,sabzi,veg korma,veg kurma,subzi,mixed vegetable,vegetable stew,stew|100|3|10|5|3|3|320|200|1 bowl
Baingan bharta|eggplant curry,brinjal curry,eggplant mash,eggplant,fried eggplant,brinjal,aubergine|90|2|9|5|3|4|250|200|1 bowl
Bhindi masala|okra curry,bhindi fry,okra|90|2.5|9|5|3.5|3|250|150|1 serving
Kadhi|kadhi pakora,yogurt curry|85|3|8|4.5|0.5|3|300|200|1 bowl
Samosa|samosas|262|4.5|28|15|2.5|1|390|100|1 large samosa
Pakora|bhaji,pakoda,onion bhaji,bhajiya,fritters|270|7|25|16|3|2|400|100|5 pieces
Pav bhaji|pav bhaji plate|150|4|20|6|3|3|450|300|1 plate
Vada pav|wada pav,bombay burger|260|6|35|11|2|3|500|150|1 vada pav
Poha|flattened rice,kanda poha|130|2.5|24|3|1.5|1.5|350|200|1 plate
Upma|rava upma,semolina upma,suji upma|120|3|18|4|2|1|350|200|1 plate
Pani puri|golgappa,puchka,sev puri,dahi puri|190|3|30|6|2|2|300|100|6 pieces
Bhel puri|bhel,chaat,papdi chaat,aloo chaat,sev|180|4|30|5|3|4|400|120|1 plate
Dhokla|khaman,khaman dhokla|160|6|25|4|2|5|450|100|4 pieces
Gulab jamun|gulab jamoon,jamun|330|4|45|15|0.5|35|50|80|2 pieces
Jalebi|imarti|380|2|60|15|0.3|40|20|60|3 pieces
Rasgulla|rosogolla,ras malai,rasmalai|186|4|40|1|0|35|40|100|2 pieces
Kheer|rice pudding,payasam,phirni,sheer khurma,seviyan|150|4|22|5|0.3|16|60|150|1 bowl
Halwa|gajar halwa,suji halwa,sooji halwa,sheera|250|4|35|11|2|28|40|100|1 serving
Ladoo|laddu,besan ladoo,motichoor,barfi,burfi,peda|420|7|55|20|2|35|40|45|1 ladoo
Lassi|sweet lassi,mango lassi,buttermilk,chaas,salted lassi|75|3|12|2|0|10|50|250|1 glass
Chai|masala chai,tea with milk,milk tea,tea,cutting chai|45|1.5|6|1.7|0|6|20|200|1 cup
Raita|cucumber raita,curd raita,boondi raita|55|2.5|5|2.5|0.3|4|150|100|1 small bowl
Papad|papadum,poppadom,pappadam|340|25|50|3|10|1|1500|15|1 papad
Indian thali|thali,meal plate,veg thali|150|5|20|5|3|3|400|500|1 thali
Hummus|houmous,chickpea dip|177|8|20|8|6|0.3|380|60|a quarter cup
Falafel|falafels|333|13|32|18|5|3|294|100|4 pieces
Pita bread|pita,pitta,flatbread,lavash|275|9|55|1.2|2.2|1|536|60|1 pita
Couscous|cous cous,bulgur|112|3.8|23|0.2|1.4|0.1|5|180|1 cup cooked
Tabbouleh|tabouleh|120|2.5|12|7|3|2|200|150|1 bowl
Baklava|baklawa|430|6|50|23|2|25|120|50|2 pieces
Moussaka|musaka|120|5|8|8|1.5|3|300|250|1 piece
Steak|grilled steak,beef steak,sirloin,ribeye,t-bone,filet mignon,beef|250|26|0|16|0|0|60|200|1 steak
Grilled chicken|grilled chicken breast,chicken breast,roast chicken,baked chicken,chicken|165|31|0|3.6|0|0|74|150|1 breast
Pork chop|pork,pork loin,pork belly,ribs,spare ribs|230|25|0|14|0|0|60|150|1 chop
Bacon|bacon strips|541|37|1.4|42|0|0|1700|16|2 slices
Sausage|sausages,bratwurst,chorizo,salami|301|12|2|27|0|1|800|100|2 links
Meatballs|meatball|197|13|8|12|1|2|450|120|5 meatballs
Meatloaf|meat loaf|190|15|8|11|0.5|3|420|150|1 slice
Fish and chips|fish n chips,fish & chips|230|11|22|11|1.6|0.5|430|350|1 plate
Salmon|grilled salmon,salmon fillet,baked salmon,smoked salmon|208|20|0|13|0|0|60|150|1 fillet
Fish fillet|white fish,cod,tilapia,baked fish,grilled fish,fish,sea bass,pomfret,basa|120|22|0|3|0|0|80|150|1 fillet
Fried fish|fish fry,fish fingers|230|18|10|13|0.5|0|400|150|1 fillet
Shrimp|prawns,prawn,shrimps,garlic shrimp,grilled shrimp|99|24|0.2|0.3|0|0|111|100|1 serving
Lobster|crab,crab cake,seafood,oysters,mussels,clams|98|20|0.5|1.2|0|0|420|120|1 serving
Mashed potatoes|mashed potato,potato mash|113|2|17|4.2|1.5|1|290|200|1 cup
Baked potato|jacket potato,boiled potato,potato|93|2.5|21|0.1|2.2|1.2|10|200|1 potato
Roasted potatoes|roast potato,potato wedges,wedges,home fries,aloo fry|149|2.5|22|5.5|2.5|1|200|150|1 serving
Hash browns|hash brown,potato rosti,tikki,aloo tikki|265|3|28|16|2.5|0.5|370|100|1 piece
Onion rings|onion ring|411|4|40|26|2.5|4|600|100|1 serving
Steamed vegetables|boiled vegetables,vegetables,veggies,sauteed vegetables,stir fry vegetables,stir fried vegetables|40|2.5|7|0.4|3|3|30|150|1 serving
Broccoli|cauliflower,green beans,spinach,cabbage|37|2.4|6|0.4|3.3|1.7|33|100|1 cup
Apple|apples|52|0.3|14|0.2|2.4|10|1|180|1 medium
Banana|bananas|89|1.1|23|0.3|2.6|12|1|120|1 medium
Orange|oranges,tangerine,mandarin,mosambi,sweet lime|47|0.9|12|0.1|2.4|9|0|130|1 orange
Strawberries|strawberry,raspberries,blueberries,blackberries,berries|45|0.8|10|0.4|2.2|6|1|150|1 cup
Grapes|grape,cherries,cherry|69|0.7|18|0.2|0.9|16|2|100|1 cup
Watermelon|melon,muskmelon,cantaloupe,honeydew|30|0.6|8|0.2|0.4|6|1|280|2 cups
Mango|mangoes|60|0.8|15|0.4|1.6|14|1|200|1 mango
Pineapple|pineapples|50|0.5|13|0.1|1.4|10|1|165|1 cup
Papaya|papayas|43|0.5|11|0.3|1.7|8|8|150|1 cup
Pear|pears,peach,peaches,plum,apricot,nectarine,guava|55|0.4|14|0.2|3|9|1|170|1 pear
Kiwi|kiwi fruit|61|1.1|15|0.5|3|9|3|75|1 kiwi
Avocado|avocados,guacamole|160|2|9|15|7|0.7|7|100|half an avocado
Pomegranate|anar|83|1.7|19|1.2|4|14|3|100|half a cup
Dates|khajoor,dried fruit,raisins,figs,dried figs|282|2.5|75|0.4|8|63|2|40|4 dates
Corn|sweet corn,corn on the cob,maize,bhutta|96|3.4|21|1.5|2.4|4.5|15|150|1 cob
Sweet potato|yam,shakarkandi|86|1.6|20|0.1|3|4.2|55|130|1 medium
Tomato|tomatoes,cherry tomato|18|0.9|3.9|0.2|1.2|2.6|5|120|1 tomato
Cucumber|cucumbers,radish,lettuce,celery,zucchini|15|0.7|3.6|0.1|1|1.7|2|100|1 serving
Carrot|carrots,beetroot,beet|41|0.9|10|0.2|2.8|4.7|69|80|1 carrot
Almonds|almond|579|21|22|50|12.5|4|1|28|a handful
Peanuts|peanut,groundnut,groundnuts,roasted peanuts|567|26|16|49|8.5|4|18|28|a handful
Cashews|cashew,pistachios,walnuts,mixed nuts,nuts,dry fruits|553|18|30|44|3.3|6|12|28|a handful
Peanut butter|nut butter|588|25|20|50|6|9|426|32|2 tbsp
Milk|whole milk,glass of milk,full cream milk,toned milk,skim milk|61|3.2|4.8|3.3|0|5|43|240|1 glass
Cheese|cheddar,cheddar cheese,mozzarella,cheese slice,parmesan,cheese cubes,feta|403|25|1.3|33|0|0.5|620|30|1 slice
Paneer|cottage cheese indian,paneer cubes,raw paneer|265|18|1.2|20|0|1|22|100|100 g
Butter|ghee,margarine|717|0.9|0.1|81|0|0.1|11|14|1 tbsp
Coffee|black coffee,americano,espresso,filter coffee|2|0.3|0|0|0|0|2|240|1 cup
Latte|cappuccino,flat white,coffee with milk,mocha,macchiato,iced latte|45|3|4.5|1.8|0|4.5|45|350|1 mug
Orange juice|fruit juice,apple juice,juice,mosambi juice,fresh juice|45|0.7|10.4|0.2|0.2|8.4|1|250|1 glass
Soda|cola,coke,pepsi,soft drink,sprite,fanta,lemonade,thums up|42|0|10.6|0|0|10.6|4|330|1 can
Beer|lager,ale,cider|43|0.5|3.6|0|0|0|4|355|1 bottle
Wine|red wine,white wine,champagne|83|0.1|2.6|0|0|0.6|4|150|1 glass
Cocktail|margarita,mojito,whiskey,vodka,rum,gin,spirits|130|0|10|0|0|9|5|150|1 drink
Milkshake|shake,thick shake,cold coffee,frappe,bubble tea,boba|112|3.5|18|3|0.3|17|80|350|1 glass
Hot chocolate|cocoa,hot cocoa|77|3.2|11|2.3|1|10|50|250|1 mug
Coconut water|nariyal pani|19|0.7|3.7|0.2|1.1|2.6|105|250|1 glass
Protein shake|whey shake|90|15|5|1.5|1|3|120|330|1 shake
Tomato soup|veg soup,vegetable soup,mushroom soup,cream soup,sweet corn soup,manchow soup|40|1|8|0.7|1|5|390|250|1 bowl
Chicken soup|chicken noodle soup,clear soup,hot and sour soup,broth,bone broth|36|3|3|1.2|0.2|0.5|400|250|1 bowl
Lentil soup|bean soup,minestrone,pea soup|60|4|9|1|3|1|350|250|1 bowl
Miso soup|miso|30|2|3|1|0.5|0.5|600|240|1 bowl
Potato chips|chips packet,crisps,lays,kurkure,namkeen,bhujia,wafers|536|7|53|34|4.8|0.3|525|30|1 small bag
Popcorn|pop corn|387|13|78|4.5|15|0.9|8|30|3 cups
Pretzel|pretzels|380|10|80|3|3|3|1200|30|a handful
Crackers|cracker,rusk,khakhra,digestive biscuit,rice cakes|430|9|66|15|3|7|800|30|5 crackers
Granola bar|energy bar,cereal bar,protein bar|420|10|60|16|5|28|250|40|1 bar
Mochi|japanese sweets,daifuku|230|3|50|0.5|1|20|10|50|2 pieces
Pastry|danish,danish pastry,puff pastry,eclair|400|6|45|22|1.5|18|300|80|1 pastry
Chicken pie|meat pie,pot pie,chicken pot pie,savory pie|260|8|24|15|1.5|2|450|200|1 pie
Cinnamon roll|cinnamon bun,sweet roll|350|5|55|13|2|25|350|100|1 roll
Garlic bread|garlic toast|350|9|45|15|2.5|3|600|60|2 slices
Bruschetta|toast with tomato|180|4|24|7|2|3|350|100|2 pieces
Risotto|rice risotto|150|4|22|5|0.5|1|350|250|1 plate
Paella|jambalaya|160|9|20|5|1|1|450|300|1 plate
Stir fry|stir fried chicken,kung pao chicken,sweet and sour chicken,orange chicken,manchurian,gobi manchurian,chilli chicken,chilli paneer,general tso chicken|170|11|14|8|1.5|6|700|250|1 plate
Tofu|tofu curry,soy chunks,soya chunks,tempeh|120|12|3|7|1|0.5|10|120|1 serving
Baked beans|beans on toast|95|5|16|0.5|5|5|350|200|1 serving
Sprouts|sprouts salad,moong sprouts,sprout chaat|40|4|6|0.4|2|1|10|100|1 bowl
Kimchi|pickled vegetables,sauerkraut|15|1|2.4|0.5|1.6|1|700|50|1 side
Bibimbap|korean rice bowl,korean bbq,bulgogi|150|6|20|5|2|3|500|350|1 bowl
Katsu|chicken katsu,tonkatsu,schnitzel,breaded cutlet|250|16|18|13|1|1|450|180|1 cutlet
Empanada|empanadas,patties,puff|270|8|28|14|1.5|2|450|100|1 piece
!Dessert|sweet,sweets,dessert,candy floss|350|4|50|15|1|30|100|100|1 portion
!Savoury dish|dish,meal,food,plate|160|8|16|7|1.5|2|400|300|1 plate
"""
}
