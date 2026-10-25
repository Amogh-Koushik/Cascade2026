# The apps an agent may put in generated notifications. Keeping the model to a fixed list means
# every package it writes has a known category in the params file, instead of invented names
# that would all fall into "other".

from typing import Any

APPS: dict[str, str] = {
    "WhatsApp": "com.whatsapp",
    "Telegram": "org.telegram.messenger",
    "Messages (SMS)": "com.google.android.apps.messaging",
    "Instagram": "com.instagram.android",
    "Snapchat": "com.snapchat.android",
    "LinkedIn": "com.linkedin.android",
    "YouTube": "com.google.android.youtube",
    "Swiggy": "in.swiggy.android",
    "Zomato": "com.application.zomato",
    "Flipkart": "com.flipkart.android",
    "Amazon": "in.amazon.mShop.android.shopping",
    "Myntra": "com.myntra.android",
    "Inshorts": "com.nis.app",
    "Gmail": "com.google.android.gm",
    "Outlook": "com.microsoft.office.outlook",
    "Slack": "com.Slack",
    "Google Pay": "com.google.android.apps.nbu.paisa.user",
    "PhonePe": "com.phonepe.app",
    "Paytm": "net.one97.paytm",
    "Uber": "com.ubercab",
    "Ola": "com.olacabs.customer",
    "Rapido": "com.rapido.passenger",
    "Google Maps": "com.google.android.apps.maps",
}

APP_NAMES: dict[str, str] = {package: name for name, package in APPS.items()}


def category_of(package: str, params: dict[str, Any]) -> str:
    return str(params["app_category_map"].get(package, "other"))


def describe_apps(names: list[str]) -> str:
    return "\n".join(f"- {name}: {APPS[name]}" for name in names)
