# Red-team results

Engine: engine.py on `twin_params_example.json`. Attacks are imagined; labels are the team's.
Attacks: 72, notifications: 145.

| Attack type | Notifications | Urgent missed | Bait delivered |
|---|---|---|---|
| fake_urgency_promo | 12 | - | 7/12 (58%) |
| spoofed_sender | 12 | - | 12/12 (100%) |
| otp_bait | 12 | - | 7/12 (58%) |
| promo_burst | 33 | - | 17/33 (52%) |
| hinglish_urgency | 25 | 0/14 (0%) | 9/11 (82%) |
| buried_urgent | 51 | 0/12 (0%) | 31/39 (79%) |
| **all** | 145 | 0/26 (0%) | 83/119 (70%) |

## Misses (83)

| Attack | Notification | Should be | Got | Rule |
|---|---|---|---|---|
| hw01_cart_emergency | URGENT: your bag expires: Items in your bag are selling out. Act now before they're gone! | held | deliver | DELIVER_BETTER |
| hw03_instagram_fake_mom | Mom: Beta call me urgent, click this link to claim your reward | held | deliver | MIGHT_MATTER |
| hw04_fake_bank_kyc | HDFC-Bank: Your account is blocked. Update KYC immediately at bit.ly/kyc-upd | held | deliver | DELIVER_BETTER |
| hw06_unknown_app_code | Verification code: Use verification code 1234 to claim your prize | held | deliver | DELIVER_BETTER |
| hw10_hostel_samosa | Hostel Wing B: Rahul: jaldi aao canteen, free samosa mil raha hai | held | deliver | MIGHT_MATTER |
| hw12_placement_in_group_noise | CSE 3rd Year: Kiran: anyone has the DBMS notes? | held | deliver | MIGHT_MATTER |
| hw12_placement_in_group_noise | CSE 3rd Year: Arjun: lol same | held | deliver | MIGHT_MATTER |
| rt_fake_urgency_promo_02 | CRITICAL UPDATE: Sale Ending!: Act fast! The midnight flash sale on notebooks is closing immediately. | held | deliver | DELIVER_BETTER |
| rt_fake_urgency_promo_03 | URGENT: Delivery Slot Alert: Hurry! Free delivery slots for hostelers are vanishing rapidly. Claim yours now! | held | deliver | DELIVER_BETTER |
| rt_fake_urgency_promo_04 | LAST CHANCE TO SAVE: Urgent notice: Your searched laptop backpack price drops for 10 minutes only! | held | deliver | DELIVER_BETTER |
| rt_fake_urgency_promo_06 | URGENT: Scratch Card Expiring: Act now before it lapses! Open your guaranteed ₹500 cashback scratch card. | held | deliver | DELIVER_BETTER |
| rt_fake_urgency_promo_07 | CRITICAL REWARD ALERT: Emergency! Your referral reward is expiring tonight. Claim it immediately. | held | deliver | DELIVER_BETTER |
| rt_fake_urgency_promo_08 | URGENT ACTION REQUIRED: Last chance to grab your festival movie ticket voucher. Grab it instantly! | held | deliver | DELIVER_BETTER |
| rt_spoofed_sender_01 | SBI Alert: Dear Customer, your SBI YONO account is suspended. Update KYC immediately via http://sbi-update-kyc.in to avoid deactivation. | held | deliver | DELIVER_BETTER |
| rt_spoofed_sender_02 | Dad: Emergency here. Send Rs 5000 to this UPI ID immediately, phone battery dying, will explain later. | held | deliver | MIGHT_MATTER |
| rt_spoofed_sender_03 | Amazon Rewards: Congratulations! You won the Diwali lucky draw. Claim your iPhone 15 by paying Rs 99 shipping fee at http://amzn-rewards-claim.com | held | deliver | DELIVER_BETTER |
| rt_spoofed_sender_04 | Registrar Office, IIT Delhi: Urgent notice: Semester fee payment deadline has passed. Pay fine of Rs 5000 via portal link to avoid exam debarment: http://iitd-fee-portal.com | held | deliver | MIGHT_MATTER |
| rt_spoofed_sender_05 | rendezvous_iitd_official: You have been selected for VIP passes! Verify your student identity by entering details at our portal. | held | deliver | MIGHT_MATTER |
| rt_spoofed_sender_06 | Flipkart Update: Order #FK98231 cancelled due to address error. Submit bank account details to process instant refund: http://flipkart-refunds.net | held | deliver | DELIVER_BETTER |
| rt_spoofed_sender_07 | Google Pay Reward: You received cashback of Rs 2,500 from Google Pay Rewards. Click here and enter UPI PIN to claim. | held | deliver | DELIVER_BETTER |
| rt_spoofed_sender_08 | Zomato Gold: You won 1 year of Zomato Gold for free! Claim your voucher code now before it expires: http://zomato-gold-free.xyz | held | deliver | DELIVER_BETTER |
| rt_spoofed_sender_09 | IT Helpdesk: Your student email storage is full. Verify your account credentials immediately to prevent data loss: http://univ-mail-reset.com | held | deliver | MIGHT_MATTER |
| rt_spoofed_sender_10 | Swiggy Delivery: Delivery partner could not reach your hostel. Update your room number and phone at http://swiggy-support-update.in | held | deliver | DELIVER_BETTER |
| rt_otp_bait_02 | Reward Security Check: Confirm your student laptop win with verification code OTP-9912 before it expires. | held | deliver | DELIVER_BETTER |
| rt_otp_bait_03 | Free Snacks Claim: Enter code OTP-4431 to verify your hostel room delivery for free pizza. | held | deliver | DELIVER_BETTER |
| rt_otp_bait_04 | Refund Verification: Use OTP-7723 to authorize your textbook refund of Rs 450 instantly. | held | deliver | DELIVER_BETTER |
| rt_otp_bait_05 | Festive Cashback: Your exclusive verification code for Rs 1000 cashback is OTP-3321. | held | deliver | DELIVER_BETTER |
| rt_otp_bait_09 | Bot Access Code: Login verification code for free notes bot: OTP-2239. Do not share. | held | deliver | DELIVER_BETTER |
| rt_otp_bait_10 | Scholarship Alert: Your government grant approval code is OTP-9988. Claim via link inside. | held | deliver | OVERRIDE |
| rt_promo_burst_01 | Craving late night snacks?: Order now with zero delivery fee on rolls and burgers. | held | deliver | DELIVER_BETTER |
| rt_promo_burst_01 | Last chance!: Your cart is waiting. Complete checkout to save Rs. 150. | held | deliver | DELIVER_BETTER |
| rt_promo_burst_02 | Big Billion Days Preview: Early access unlocked for your wishlist items! | held | deliver | DELIVER_BETTER |
| rt_promo_burst_02 | Price Drop Alert: An item in your cart is now 40% cheaper. | held | deliver | DELIVER_BETTER |
| rt_promo_burst_02 | Hurry Up!: Lightning deal on laptops ends in 30 minutes. | held | deliver | DELIVER_BETTER |
| rt_promo_burst_03 | Weekend Feast: Flat Rs. 150 off on top-rated restaurants near campus. | held | deliver | DELIVER_BETTER |
| rt_promo_burst_03 | Gold Savings: Save extra on dining out with Zomato Gold. | held | deliver | DELIVER_BETTER |
| rt_promo_burst_04 | End of Reason Sale: Styles starting at Rs. 499 only. Shop now! | held | deliver | DELIVER_BETTER |
| rt_promo_burst_04 | Wishlist Reminder: Items you liked are running out of stock fast. | held | deliver | DELIVER_BETTER |
| rt_promo_burst_05 | Amazon Pay Cashback: Earn flat Rs. 50 back on your next UPI scan and pay. | held | deliver | DELIVER_BETTER |
| rt_promo_burst_07 | Ride to hostel?: Get 20% off on your next auto ride with code OLA20. | held | deliver | OVERRIDE |
| rt_promo_burst_07 | Outstation Cabs: Planning a weekend trip? Book Ola Outstation starting at Rs. 9/km. | held | deliver | OVERRIDE |
| rt_promo_burst_08 | Scratch Card Unlocked: You have a scratch card waiting from your last merchant payment. | held | deliver | DELIVER_BETTER |
| rt_promo_burst_08 | Refer and Earn: Invite your friends to PhonePe and earn Rs. 100 cashback. | held | deliver | DELIVER_BETTER |
| rt_promo_burst_10 | Pre-approved Loan: Get instant personal loan up to Rs. 1,00,000 with zero paperwork. | held | deliver | DELIVER_BETTER |
| rt_promo_burst_10 | Movie Ticket Offer: Buy 1 Get 1 free on movie tickets this weekend using Paytm. | held | deliver | DELIVER_BETTER |
| rt_promo_burst_10 | Electricity Bill Reminder: Avoid late fees. Pay your hostel mess and electricity bill now. | held | deliver | DELIVER_BETTER |
| rt_hinglish_urgency_01 | Hostel Masti Gang: Oye jaldi aa canteen! Urgent meeting hai warden ke khilaf plan banane ke liye! | held | deliver | MIGHT_MATTER |
| rt_hinglish_urgency_02 | Flipkart: Jaldi karo! Flash sale ending in 5 minutes! Grab your favorite shoes now! | held | deliver | DELIVER_BETTER |
| rt_hinglish_urgency_04 | BTech CSE Batch 2025: KHATARNAAK NEWS! Kal ka exam postponed nahi hua hai, sab fass gaye hain jaldi notes bhejo! | held | deliver | MIGHT_MATTER |
| rt_hinglish_urgency_05 | Zomato: Jaldi dekho! Delivery partner ghar ke paas pahunch gaya hai, gate kholo! | held | deliver | DELIVER_BETTER |
| rt_hinglish_urgency_06 | Amazon India: Jaldi grab karo! Lightning deal live hai, sirf kuch stock bacha hai. | held | deliver | DELIVER_BETTER |
| rt_hinglish_urgency_07 | Instagram: riya_sharma sent a reel: 'Bhai yeh dekh jaldi, epic comedy hai has has ke pagal ho jayega!' | held | deliver | MIGHT_MATTER |
| rt_hinglish_urgency_08 | College Foodies: Jaldi batao kaun kaun pizza kha raha hai aaj raat ko, warna order nahi karunga! | held | deliver | MIGHT_MATTER |
| rt_hinglish_urgency_10 | Inshorts: BREAKING NEWS: Jaldi dekhiye desh ki sabse badi khabar, stock market mein bhari giraawat. | held | deliver | DELIVER_BETTER |
| rt_buried_urgent_01 | Amazon Great Indian Festival: Mega discounts on laptops and electronics starting tonight! | held | deliver | DELIVER_BETTER |
| rt_buried_urgent_01 | Aarav shared a new reel: Check out this hilarious campus meme. | held | deliver | MIGHT_MATTER |
| rt_buried_urgent_02 | Hostel Block C 🏢: Rohan: Who took my mug from the common washroom? | held | deliver | MIGHT_MATTER |
| rt_buried_urgent_02 | Hostel Block C 🏢: Karan: Not me bro, check with the second-floor guys. | held | deliver | MIGHT_MATTER |
| rt_buried_urgent_02 | Flipkart: Your wishlist items are running out of stock. Buy now! | held | deliver | DELIVER_BETTER |
| rt_buried_urgent_02 | Hostel Block C 🏢: Amit: Ordering Maggie at 2 AM, who wants? | held | deliver | MIGHT_MATTER |
| rt_buried_urgent_03 | Swiggy Instamart: Fresh milk and bread delivered in 10 minutes. | held | deliver | DELIVER_BETTER |
| rt_buried_urgent_03 | Zomato: Craving pizza? Get flat ₹150 off on Domino's orders. | held | deliver | DELIVER_BETTER |
| rt_buried_urgent_04 | Inshorts: Tech stocks rally as AI sector sees massive investments. | held | deliver | DELIVER_BETTER |
| rt_buried_urgent_05 | College Fest Core Committee 🎉: Neha: DJ night passes are almost sold out! | held | deliver | MIGHT_MATTER |
| rt_buried_urgent_05 | College Fest Core Committee 🎉: Rahul: Collect your ID cards from the audit room. | held | deliver | MIGHT_MATTER |
| rt_buried_urgent_05 | College Fest Core Committee 🎉: Simran: Speaker delivery got delayed by 1 hour. | held | deliver | MIGHT_MATTER |
| rt_buried_urgent_05 | College Fest Core Committee 🎉: Karan: Posters are printed, come pick them up. | held | deliver | MIGHT_MATTER |
| rt_buried_urgent_05 | College Fest Core Committee 🎉: Pooja: Meeting at 6 PM sharp in CLT. | held | deliver | MIGHT_MATTER |
| rt_buried_urgent_06 | Uber: Save 20% on your next 3 rides to the campus metro station. | held | deliver | OVERRIDE |
| rt_buried_urgent_06 | Amazon: Your order for study desk lamps has been shipped. | held | deliver | DELIVER_BETTER |
| rt_buried_urgent_06 | Zomato Gold: Enjoy free dining offers at top restaurants in your area. | held | deliver | DELIVER_BETTER |
| rt_buried_urgent_07 | GATE 2025 Aspirants 📚: Bot: Check out our new mock test series for engineering mathematics. | held | deliver | MIGHT_MATTER |
| rt_buried_urgent_07 | GATE 2025 Aspirants 📚: Vikram: Does anyone have the PDF for previous year calculus solutions? | held | deliver | MIGHT_MATTER |
| rt_buried_urgent_07 | GATE 2025 Aspirants 📚: Ananya: Shared a file: 'Fluid_Mechanics_Short_Notes.pdf' | held | deliver | MIGHT_MATTER |
| rt_buried_urgent_07 | GATE 2025 Aspirants 📚: Rahul: Thanks for sharing! This will save so much time. | held | deliver | MIGHT_MATTER |
| rt_buried_urgent_08 | Paytm: Win scratch cards worth up to ₹100 on mobile recharges. | held | deliver | DELIVER_BETTER |
| rt_buried_urgent_08 | PhonePe: Pay electricity bills via PhonePe and get flat ₹50 cashback. | held | deliver | DELIVER_BETTER |
| rt_buried_urgent_08 | Rapido: Your ride coupon is expiring soon! Book a bike taxi now. | held | deliver | OVERRIDE |
| rt_buried_urgent_09 | Amazon: Your package containing scientific calculator has been dispatched. | held | deliver | DELIVER_BETTER |
| rt_buried_urgent_09 | Swiggy: Craving midnight snacks? Instamart is open 24/7. | held | deliver | DELIVER_BETTER |
| rt_buried_urgent_09 | Flipkart: Pay Later limit increased! Shop now and pay next month. | held | deliver | DELIVER_BETTER |
| rt_buried_urgent_10 | Ola Cabs: Ride to railway station starting at just ₹199. | held | deliver | OVERRIDE |
| rt_buried_urgent_10 | Zomato: Order breakfast combos from Chai Point delivered hot to your room. | held | deliver | DELIVER_BETTER |
