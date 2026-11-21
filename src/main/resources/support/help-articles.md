<!--
Help articles used by the AI support assistant (POST /api/support/chat).
The assistant may only answer from these articles, so keep them accurate.

Format: each article starts with a line
  ## <id> | <audience> | <title>
where <audience> is client, owner or all. Everything until the next "## "
line is the article body. Write in English; the assistant translates answers.
Use the button and tab names exactly as they appear in the English app.
-->

## book | client | How to book an appointment
1. Find a salon from the Home tab, or tap Search to search by salon name, service or area.
2. Open the salon, select one or more services, then tap the clock (schedule) button at the bottom, next to the total price.
3. Pick a day and an available time slot, then tap Confirm Time.
4. Review your booking, add notes for the salon if you want, then tap Confirm.
Payment is made at the salon.
Your booking starts as "Pending" (waiting for salon confirmation). The salon must confirm it within 15 minutes; if it doesn't, the request is cancelled automatically and the time slot becomes free again, so you can book another time.
Time slots that start in less than 30 minutes are not shown.

## smart-search | client | Searching in your own words
On the Search screen, typing shows results by salon name, address or description.
For a smart search, type what you want in your own words, in English, French or Tunisian Arabic (for example "manicure in La Marsa on Saturday under 40 dinars"), then press Enter or tap the sparkle button.
The filters that were understood (service, place, price, day and time) appear as chips above the results. Tap Clear to go back to regular results.

## booking-status | client | What booking statuses mean
- Pending: you sent a booking request and the salon has not confirmed it yet. Unconfirmed requests are cancelled automatically after 15 minutes.
- Confirmed: the salon accepted your booking.
- Completed: the salon marked your appointment as done.
- Cancelled: the booking was cancelled by you or the salon, or expired without confirmation.
- No-show: the appointment was missed.
You can see your bookings in the Bookings tab, under Upcoming and Past.

## cancel | client | How to cancel a booking
Go to the Bookings tab, open Upcoming, find the appointment and tap Cancel, then confirm.
You can cancel bookings that are Pending or Confirmed.
Cancelling less than 12 hours before the appointment starts counts as a late cancellation and lowers your reliability score by 10 points. Cancelling earlier has no effect on your score.

## reschedule | client | How to change the time of a booking
Go to the Bookings tab, open Upcoming, find the appointment and tap Reschedule. Pick a new day and time slot and tap Confirm Reschedule.
You can reschedule bookings that are Pending or Confirmed, to a time that is still free.
After rescheduling, the booking becomes Pending again and the salon must confirm the new time.

## reliability | client | Your reliability score
Every client has a reliability score that helps salons avoid missed appointments. It starts at 100 and can go up to 120.
- Completing an appointment: +2 points, plus a 5-point bonus every 3 completed appointments in a row.
- Missing an appointment (no-show): -20 points.
- Late cancellation (less than 12 hours before the start): -10 points.
- One week with no no-shows and no late cancellations: +1 point.
Restrictions:
- Below 50: you must confirm attendance for your bookings, and you can have at most 2 active bookings at a time.
- Below 30: you can have only 1 active booking at a time.
- Below 20: you cannot make new bookings in the app; please contact support.
- Below 10: booking is suspended for 7 days.
Active bookings are those that are Pending or Confirmed.

## confirm-attendance | client | Confirming attendance
If your reliability score is below 50, your confirmed bookings show "Attendance confirmation required".
Go to the Bookings tab, open Upcoming and tap Confirm Attendance on that appointment.
You must confirm more than 3 hours before the appointment starts. If you don't, the booking is cancelled automatically and the time slot is given back to the salon.

## cant-book | client | Why can't I book?
Common reasons:
- The time slot was just taken by someone else. Choose another time.
- You already have an active appointment at the same salon on that day. Only one booking per salon per day is allowed.
- You reached your active booking limit because of a low reliability score. Complete or cancel an existing booking first.
- Your booking access is suspended or your reliability score is below 20.
- The salon is closed that day, or no slots are free. You can join the waitlist instead.
- The date is in the past.

## waitlist | client | Joining the waitlist
When a day is fully booked, tap Join Waitlist on the time slot screen.
You can see your waitlist in the Bookings tab, under Waitlist, and tap Leave to remove yourself.
If a confirmed booking at that salon is cancelled for that day, the first person on the waitlist sees "A slot opened up — book now!". The slot is not reserved for you, so book it quickly by tapping Book.

## payment | client | Payments
Payment is made at the salon, at your appointment. The app does not currently take card payments or store payment cards.
For questions about prices, charges or refunds, contact the salon directly, or email support@loomea.app.

## reviews | client | Writing a review
Open the salon's page, scroll to Reviews and tap Write a Review. Choose a rating, add a comment if you want, and tap Submit.
You can write one review per salon. You can delete your own review.

## saved-salons | client | Saving favourite salons
Tap the heart icon on a salon to save it. Your saved salons are in the Saved Salons tab. Tap the heart again to remove it.

## account | all | Your account, sign-in and password
- Sign up with your email address. We send a 6-digit verification code by email; it expires after 10 minutes, and you can ask for a new one.
- If you sign in before verifying your email, a new code is sent.
- Forgot your password? On the sign-in screen tap Forgot Password? and follow the link we email you.
- To change your name, phone number, location or photo, go to the Profile tab and tap Edit Profile.
- You can turn on biometric sign-in (fingerprint or face) in the Profile tab.

## delete-account | all | Deleting your account
Go to the Profile tab, tap Delete Account and type "delete" to confirm.
Your account is permanently deleted after 30 days. Until then you can tap Cancel Account Deletion in the Profile tab to keep your account.

## contact | all | Contacting support
If the assistant can't help, email support@loomea.app. For questions about a specific appointment, you can also contact the salon directly.

## owner-setup | owner | Setting up your salon
After creating a salon owner account, you are guided through setting up your salon in 4 steps: salon details (name, description, address, phone), the services you offer, your location on the map, and a salon photo. Tap Done at the end.
You can change these details later from the My Salon tab.

## owner-services | owner | Managing services, hours and portfolio
In the My Salon tab:
- Services: tap Add Service and enter the name, price and duration in minutes. Use Edit or Delete on an existing service.
- Working Hours: tap Edit to set opening and closing times for each day, or mark a day as closed. Clients can only book inside your working hours. If you don't set hours, clients can book from 09:00 to 20:00.
- Portfolio: tap Add Post to add photos of your work.
- Reviews from clients are shown at the bottom.

## owner-bookings | owner | Managing booking requests
Open the Bookings tab to see Upcoming, Past and All bookings.
- New requests are Pending. Tap Confirm to accept or Reject to decline (you can give a reason).
- You must confirm a request within 15 minutes, otherwise it is cancelled automatically.
- Confirming a booking automatically cancels other pending requests for the same time.
- After the appointment, tap Complete. This gives the client +2 reliability points.
- Important: confirmed appointments that are not marked Complete within 2 hours after their end time are automatically marked as a no-show, and the client loses 20 reliability points. Mark appointments Complete promptly.
- You can cancel a confirmed booking with Cancel. If clients are on the waitlist for that day, the first one is notified that a slot opened.

## owner-reliability | owner | Client reliability scores
Each booking shows the client's reliability score. Clients lose points for no-shows (-20) and late cancellations less than 12 hours before (-10), and gain points for completed appointments.
Clients with a score below 50 must confirm attendance more than 3 hours before the appointment, or the booking is cancelled automatically and the slot is freed for you.
Clients with low scores can hold only 1 or 2 active bookings, and clients below 20 cannot book.

## owner-dashboard | owner | Dashboard and insights
The Dashboard tab shows today's revenue, this month's revenue from completed appointments, today's and upcoming appointment counts, and today's schedule.
The Insights card compares the last 28 days with the 28 days before: bookings, revenue, cancellation and no-show rates, returning clients, your busiest and quietest times, top services, and AI suggestions. Insights appear once you have at least 5 bookings in the last 28 days.
