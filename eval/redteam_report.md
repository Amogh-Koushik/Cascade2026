# Red-team results

Engine: engine.py on `twin_params_example.json`. Attacks are imagined; labels are the team's.
Attacks: 12, notifications: 22.

| Attack type | Notifications | Urgent missed | Bait delivered |
|---|---|---|---|
| fake_urgency_promo | 2 | - | 1/2 (50%) |
| spoofed_sender | 2 | - | 2/2 (100%) |
| otp_bait | 2 | - | 1/2 (50%) |
| promo_burst | 7 | - | 0/7 (0%) |
| hinglish_urgency | 2 | 0/1 (0%) | 1/1 (100%) |
| buried_urgent | 7 | 0/2 (0%) | 2/5 (40%) |
| **all** | 22 | 0/3 (0%) | 7/19 (37%) |

## Misses (7)

| Attack | Notification | Should be | Got | Rule |
|---|---|---|---|---|
| hw01_cart_emergency | URGENT: your bag expires: Items in your bag are selling out. Act now before they're gone! | held | deliver | DELIVER_BETTER |
| hw03_instagram_fake_mom | Mom: Beta call me urgent, click this link to claim your reward | held | deliver | MIGHT_MATTER |
| hw04_fake_bank_kyc | HDFC-Bank: Your account is blocked. Update KYC immediately at bit.ly/kyc-upd | held | deliver | DELIVER_BETTER |
| hw06_unknown_app_code | Verification code: Use verification code 1234 to claim your prize | held | deliver | DELIVER_BETTER |
| hw10_hostel_samosa | Hostel Wing B: Rahul: jaldi aao canteen, free samosa mil raha hai | held | deliver | MIGHT_MATTER |
| hw12_placement_in_group_noise | CSE 3rd Year: Kiran: anyone has the DBMS notes? | held | deliver | MIGHT_MATTER |
| hw12_placement_in_group_noise | CSE 3rd Year: Arjun: lol same | held | deliver | MIGHT_MATTER |
