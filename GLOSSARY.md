# Subscription Master

Tracks a person's recurring paid services by reading their billing emails, and reminds them before money leaves their account.

## People and access

**User**:
A person who signs in with their Google account and owns a set of Subscriptions.
_Avoid_: Account, customer, member

**Gmail connection**:
A User's standing permission for the app to read their billing emails and send reminders from their own mailbox. It is either connected or disconnected; a disconnected one needs the User to reconnect.
_Avoid_: Integration, link, mailbox access

**Login code**:
A short-lived, single-use proof that a User just signed in with Google, traded once for the app's own session.
_Avoid_: Auth code, ticket

## Subscriptions

**Subscription**:
A recurring paid service from one Merchant that a User is billed for. Every Subscription comes from at least one receipt Billing email; Users cannot add one by hand.
_Avoid_: Plan, membership, service

**Merchant**:
The company that bills a User for a Subscription, such as Netflix or Spotify.
_Avoid_: Vendor, provider, sender

**Payment processor**:
A company that sends receipts on a Merchant's behalf, such as Stripe, Paddle, Google Play or Apple; the Merchant is read from the receipt, not from the sender.
_Avoid_: Gateway, reseller

**Billing cycle**:
How often a Subscription charges: weekly, monthly or annually.
_Avoid_: Frequency, period, interval

**Payment**:
One charge for a Subscription, evidenced by one receipt or invoice.
_Avoid_: Transaction, charge record

**Billing email**:
An email from a Merchant or its Payment processor about a Subscription: a receipt, a cancellation, or a payment failure. It counts only when Gmail confirms the sender's domain and it is not a reply, forward, mailing-list thread, payment-due reminder, free-plan notice or newsletter.
_Avoid_: Receipt (when meaning any billing email), message

**Sync**:
One pass over a User's mailbox that turns new Billing emails into Subscriptions and Payments.
_Avoid_: Import, scan, crawl

**Hidden subscription**:
A Subscription the User set aside as a false positive or not worth tracking; it stays hidden through every later Sync.
_Avoid_: Ignored, archived, deleted

**Needs review**:
A Subscription whose amount, currency or Billing cycle was guessed rather than read, waiting for the User to confirm or correct it.
_Avoid_: Unverified, unconfirmed

## Status

**Active**:
A Subscription whose latest receipt covers the current Billing cycle.

**Cancelled**:
A Subscription the Merchant or the User has ended, and whose paid Billing cycle has run out.
_Avoid_: Canceled, ended, inactive

**Billing required**:
A Subscription whose last payment failed, or whose expected Payment is overdue with no receipt.
_Avoid_: Past due, failed, on hold

**Access end**:
The day a cancelled Subscription stops being usable: the date the cancellation email names, or else the end of the paid Billing cycle. Until then it is still Active and is never treated as overdue.
_Avoid_: Expiry, period end, end date

**Status change**:
A recorded move of a Subscription from one status to another, with its reason and the Billing email or User action that caused it.
_Avoid_: Status event, transition log

## Reminders

**Reminder**:
A notice to a User that a Subscription will charge soon, sent once per Billing cycle.
_Avoid_: Alert, due notice

**Notification**:
A message to a User about one of their Subscriptions, either a Reminder or a recent Status change, delivered in the app, by email from their own Gmail, or as a browser push.
_Avoid_: Alert, message, push (when meaning any channel)
