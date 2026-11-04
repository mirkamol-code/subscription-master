# Subscription Master

Tracks a person's recurring paid services by reading their billing emails, and reminds them before money leaves their account.

## People and access

**User**:
A person who signs in with their Google account and owns a set of Subscriptions.
_Avoid_: Account, customer, member

**Gmail connection**:
A User's standing permission for the app to read their billing emails and send reminders from their own mailbox. It is either connected or disconnected; a disconnected one needs the User to reconnect.
_Avoid_: Integration, link, mailbox access

**Admin**:
A User who can see every User, Subscription and Payment across the app and promote or demote other Users. There is always at least one Admin.
_Avoid_: Superuser, staff, moderator

**Login event**:
One successful sign-in by a User, with its time, IP address and browser, kept as login history.
_Avoid_: Session, visit, login log

**Last login**:
The time of a User's most recent Login event.
_Avoid_: Last seen, last active

**Login code**:
A short-lived, single-use proof that a User just signed in with Google, traded once for the app's own session.
_Avoid_: Auth code, ticket

## Subscriptions

**Subscription**:
A recurring paid service from one Merchant account that a User is billed for. Every Subscription comes from at least one receipt Billing email; Users cannot add one by hand.
_Avoid_: Membership, service

**Plan**:
The tier of a Subscription as the Merchant names it, such as Telegram Premium or 200 GB.
_Avoid_: Tier, package

**Merchant account**:
One of a User's customer accounts at a Merchant, known by the customer ID the Merchant prints on its invoices. Each Merchant account is its own Subscription.
_Avoid_: Account (alone), customer number

**Merchant**:
The company that bills a User for a Subscription, such as Netflix or Spotify.
_Avoid_: Vendor, provider, sender

**Payment processor**:
A company that sends receipts on a Merchant's behalf, such as Stripe or Paddle; the Merchant is read from the receipt, not from the sender.
_Avoid_: Gateway, reseller

**Billing platform**:
A store that bills a User for other Merchants' Subscriptions and sends the invoice itself, such as Apple, Google Play or PayPal. One invoice can bill several Subscriptions; the Subscription is "billed through" the Billing platform.
_Avoid_: Store, payment processor, reseller

**Payment method**:
The card or wallet a Subscription is charged to, known only by its card brand and last four digits, never the full card number.
_Avoid_: Card details, billing method

**Billing cycle**:
How often a Subscription charges: weekly, monthly or annually.
_Avoid_: Frequency, period, interval

**Payment**:
One charge for a Subscription, evidenced by one receipt or invoice.
_Avoid_: Transaction, charge record

**Billing email**:
An email from a Merchant, its Payment processor or a Billing platform about a Subscription: a receipt, a cancellation, or a payment failure. It counts only when Gmail confirms the sender's domain and it is not a reply, forward, mailing-list thread, payment-due reminder, free-plan notice or newsletter.
_Avoid_: Receipt (when meaning any billing email), message

**Exchange rate**:
UZS per one unit of a currency, used to convert receipt prices.
_Avoid_: Conversion rate, FX rate

**Rate source**:
Where rates come from: CBU first, open.er-api.com as fallback.
_Avoid_: Rate provider, rate API

**Saved rate**:
The last rate stored in the database, used when every source fails.
_Avoid_: Cached rate

**Sync**:
One pass over a User's mailbox that turns new Billing emails into Subscriptions and Payments.
_Avoid_: Import, scan, crawl

**Hidden subscription**:
A Subscription the User set aside as a false positive or not worth tracking; it stays hidden through every later Sync.
_Avoid_: Ignored, archived, deleted

**Needs review**:
A Subscription whose amount, currency or Billing cycle was guessed rather than read, or whose renewal receipt arrived with an unreadable amount, waiting for the User to confirm or correct it. A receipt without an amount is never a Payment until the User confirms the amount.
_Avoid_: Unverified, unconfirmed

**Similar invoices**:
A reason a Subscription Needs review: it was found only from two or more invoices of an unknown Merchant about one Billing cycle apart, and waits for the User to confirm it or hide it.
_Avoid_: Unconfirmed subscription, suspected subscription

**Awaiting account**:
A Billing email that names no customer ID from a Merchant the User has several Merchant accounts with, waiting for the User to pick which Merchant account it belongs to.
_Avoid_: Unassigned email, orphan receipt

**Line item**:
One charge on a Billing platform invoice. Each recurring Line item is its own Subscription; a one-time purchase is not.
_Avoid_: Invoice row, product

**Category**:
The kind of service a Subscription is, such as Entertainment or Infrastructure. It is either chosen by the User, which always wins, or detected with a confidence of high, medium or low; Other means not sure.
_Avoid_: Type, tag, group

**Brand logo**:
The reviewed image of a known Merchant shown next to its Subscriptions. A Merchant without one shows its Category icon instead.
_Avoid_: Favicon, avatar, icon

**Price change**:
A change in what a Subscription charges per Billing cycle, in the same currency, announced by the Merchant or seen on a newer invoice, with the old price, the new price and the day it takes effect when known.
_Avoid_: Price increase, repricing

**Usage-based billing**:
Billing whose amount follows what the User used that cycle, such as server hours, so a different amount is never a Price change.
_Avoid_: Metered, variable price

## Status

**Free trial**:
A Subscription the User has started without paying yet. It is never a Payment or current spending, and becomes Active on its first paid invoice.
_Avoid_: Trial period, free plan, $0 subscription

**Trial end**:
The day a Free trial stops being free, as the Merchant's email states it. It is unknown when the email doesn't say.
_Avoid_: Trial expiry, conversion date

A Subscription whose latest receipt covers the current Billing cycle.

**Cancelled**:
A Subscription the Merchant or the User has ended, and whose paid Billing cycle has run out.
_Avoid_: Canceled, ended, inactive

**Billing required**:
A Subscription whose last payment failed, or whose expected Payment is overdue with no receipt.
_Avoid_: Past due, failed, on hold

**Access end**:
The day a cancelled Subscription stops being usable: the date the cancellation email names, or else the end of the paid Billing cycle. Until then it is still Active (or still a Free trial) and is never treated as overdue or as an upcoming charge.
_Avoid_: Expiry, period end, end date

**Status change**:
A recorded move of a Subscription from one status to another, with its reason and the Billing email or User action that caused it.
_Avoid_: Status event, transition log

## Reminders

**Reminder**:
A notice to a User that a Subscription will charge soon, sent once per Billing cycle.
_Avoid_: Alert, due notice

**Trial-ending reminder**:
A notice to a User that a Free trial is about to reach its Trial end, sent only when the Trial end is known and the trial wasn't cancelled.
_Avoid_: Trial alert

**Notification**:
A message to a User about one of their Subscriptions, either a Reminder or a recent Status change, delivered in the app, by email from their own Gmail, or as a browser push.
_Avoid_: Alert, message, push (when meaning any channel)

## Reports

**Report column**:
One of the fixed pieces of Subscription information a User can choose to include in a downloaded report.
_Avoid_: Field, report option

## Apple clean-up

**Legacy Apple row**:
A Subscription from before Billing platforms existed that lumps all of a User's Apple charges into one row.
_Avoid_: Old Apple subscription, Apple bundle

**Held Apple email**:
A Billing email from Apple that waits, unrecorded, while the User still has a Legacy Apple row, so the same charges are never counted twice.
_Avoid_: Queued email, blocked email (Awaiting account is a different wait)

**Apple clean-up**:
The one-off rebuild of a Legacy Apple row into one Subscription per Line item from the User's original Apple emails. Its dry run only reports what would change; its apply step makes the change after a backup.
_Avoid_: Migration, re-sync, re-detection (when meaning the apply step)

**Earlier Apple purchases**:
A closed, read-only Subscription that keeps the Payments of a Legacy Apple row whose emails no longer exist. It counts in past spending, never in renewals.
_Avoid_: Apple history, archived Apple row

