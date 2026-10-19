package com.plateforme.auth.service;

import java.util.Locale;
import java.util.Map;

/**
 * Plain-text one-time-code e-mails in the interface languages (en, fr, es, pt, ar, ru, zh).
 * Unknown or missing locales fall back to English.
 */
final class VerificationEmailTemplates {

    /** Which e-mail goes out: confirming a sign-up, or choosing a password (set up or reset). */
    enum Kind { VERIFY_EMAIL, PASSWORD }

    record Email(String subject, String body) {}

    /** {code}, {minutes} and {name} are substituted; subject also carries the code for inbox previews. */
    private record Template(String subject, String greeting, String greetingNoName, String body) {}

    private static final Map<String, Template> VERIFY_EMAIL = Map.of(
            "en", new Template(
                    "{code} is your Skraft verification code",
                    "Hi {name},",
                    "Hi,",
                    """
                    Here is your code to confirm your email address and finish creating your Skraft account:

                        {code}

                    It expires in {minutes} minutes. Never share this code with anyone — Skraft will never ask you for it.

                    If you didn't create a Skraft account, you can ignore this email."""),
            "fr", new Template(
                    "{code} est votre code de vérification Skraft",
                    "Bonjour {name},",
                    "Bonjour,",
                    """
                    Voici votre code pour confirmer votre adresse e-mail et terminer la création de votre compte Skraft :

                        {code}

                    Il expire dans {minutes} minutes. Ne communiquez jamais ce code — Skraft ne vous le demandera jamais.

                    Si vous n'avez pas créé de compte Skraft, ignorez simplement cet e-mail."""),
            "es", new Template(
                    "{code} es tu código de verificación de Skraft",
                    "Hola, {name}:",
                    "Hola:",
                    """
                    Este es tu código para confirmar tu correo electrónico y terminar de crear tu cuenta de Skraft:

                        {code}

                    Caduca en {minutes} minutos. No compartas este código con nadie: Skraft nunca te lo pedirá.

                    Si no creaste una cuenta en Skraft, puedes ignorar este correo."""),
            "pt", new Template(
                    "{code} é o seu código de verificação do Skraft",
                    "Olá, {name},",
                    "Olá,",
                    """
                    Aqui está o seu código para confirmar seu e-mail e concluir a criação da sua conta no Skraft:

                        {code}

                    Ele expira em {minutes} minutos. Nunca compartilhe este código — o Skraft nunca vai pedi-lo.

                    Se você não criou uma conta no Skraft, ignore este e-mail."""),
            "ar", new Template(
                    "{code} هو رمز التحقق الخاص بك في Skraft",
                    "مرحبًا {name}،",
                    "مرحبًا،",
                    """
                    إليك رمز تأكيد بريدك الإلكتروني لإكمال إنشاء حسابك في Skraft:

                        {code}

                    تنتهي صلاحيته خلال {minutes} دقيقة. لا تشارك هذا الرمز مع أي شخص — لن يطلبه منك Skraft أبدًا.

                    إذا لم تنشئ حسابًا في Skraft، يمكنك تجاهل هذه الرسالة."""),
            "ru", new Template(
                    "{code} — ваш код подтверждения Skraft",
                    "Здравствуйте, {name}!",
                    "Здравствуйте!",
                    """
                    Ваш код для подтверждения адреса электронной почты и завершения регистрации в Skraft:

                        {code}

                    Код действует {minutes} минут. Никому не сообщайте его — Skraft никогда его у вас не попросит.

                    Если вы не создавали аккаунт в Skraft, просто проигнорируйте это письмо."""),
            "zh", new Template(
                    "{code} 是你的 Skraft 验证码",
                    "{name}，你好：",
                    "你好：",
                    """
                    以下是用于确认邮箱地址并完成 Skraft 账户注册的验证码：

                        {code}

                    验证码将在 {minutes} 分钟后失效。请勿向任何人透露此验证码——Skraft 绝不会向你索取。

                    如果你没有注册 Skraft 账户，请忽略此邮件。""")
    );

    /* One text for both "create a password" (signed in, account made with Google) and "forgot
       password": either way the code lets someone choose the account's password, which is what the
       reader must recognise — and refuse if it wasn't them. */
    private static final Map<String, Template> PASSWORD = Map.of(
            "en", new Template(
                    "{code} is your Skraft password code",
                    "Hi {name},",
                    "Hi,",
                    """
                    Someone asked to choose a new password for your Skraft account. Here is your code:

                        {code}

                    It expires in {minutes} minutes. Never share this code with anyone — Skraft will never ask you for it.

                    If this wasn't you, ignore this email: your password stays unchanged."""),
            "fr", new Template(
                    "{code} est votre code de mot de passe Skraft",
                    "Bonjour {name},",
                    "Bonjour,",
                    """
                    Une demande a été faite pour choisir un nouveau mot de passe pour votre compte Skraft. Voici votre code :

                        {code}

                    Il expire dans {minutes} minutes. Ne communiquez jamais ce code — Skraft ne vous le demandera jamais.

                    Si vous n'êtes pas à l'origine de cette demande, ignorez cet e-mail : votre mot de passe reste inchangé."""),
            "es", new Template(
                    "{code} es tu código de contraseña de Skraft",
                    "Hola, {name}:",
                    "Hola:",
                    """
                    Se ha solicitado elegir una nueva contraseña para tu cuenta de Skraft. Este es tu código:

                        {code}

                    Caduca en {minutes} minutos. No compartas este código con nadie: Skraft nunca te lo pedirá.

                    Si no fuiste tú, ignora este correo: tu contraseña no cambiará."""),
            "pt", new Template(
                    "{code} é o seu código de senha do Skraft",
                    "Olá, {name},",
                    "Olá,",
                    """
                    Foi solicitada a escolha de uma nova senha para a sua conta no Skraft. Aqui está o seu código:

                        {code}

                    Ele expira em {minutes} minutos. Nunca compartilhe este código — o Skraft nunca vai pedi-lo.

                    Se não foi você, ignore este e-mail: sua senha continua a mesma."""),
            "ar", new Template(
                    "{code} هو رمز كلمة المرور الخاص بك في Skraft",
                    "مرحبًا {name}،",
                    "مرحبًا،",
                    """
                    طلب أحدهم اختيار كلمة مرور جديدة لحسابك في Skraft. إليك الرمز:

                        {code}

                    تنتهي صلاحيته خلال {minutes} دقيقة. لا تشارك هذا الرمز مع أي شخص — لن يطلبه منك Skraft أبدًا.

                    إذا لم تكن أنت من قدّم الطلب، تجاهل هذه الرسالة: ستبقى كلمة مرورك كما هي."""),
            "ru", new Template(
                    "{code} — ваш код для пароля Skraft",
                    "Здравствуйте, {name}!",
                    "Здравствуйте!",
                    """
                    Поступил запрос на выбор нового пароля для вашего аккаунта Skraft. Ваш код:

                        {code}

                    Код действует {minutes} минут. Никому не сообщайте его — Skraft никогда его у вас не попросит.

                    Если это были не вы, просто проигнорируйте письмо: ваш пароль не изменится."""),
            "zh", new Template(
                    "{code} 是你的 Skraft 密码验证码",
                    "{name}，你好：",
                    "你好：",
                    """
                    有人请求为你的 Skraft 账户设置新密码。你的验证码是：

                        {code}

                    验证码将在 {minutes} 分钟后失效。请勿向任何人透露此验证码——Skraft 绝不会向你索取。

                    如果这不是你本人的操作，请忽略此邮件，你的密码不会改变。""")
    );

    private VerificationEmailTemplates() {
    }

    static Email render(Kind kind, String locale, String fullName, String code, int minutes) {
        Map<String, Template> templates = kind == Kind.PASSWORD ? PASSWORD : VERIFY_EMAIL;
        Template template = templates.getOrDefault(language(locale), templates.get("en"));
        String name = fullName == null ? "" : fullName.trim();
        String greeting = name.isEmpty() ? template.greetingNoName() : template.greeting().replace("{name}", name);
        String body = greeting + "\n\n" + template.body()
                .replace("{code}", code)
                .replace("{minutes}", String.valueOf(minutes))
                + "\n\n— Skraft";
        return new Email(template.subject().replace("{code}", code), body);
    }

    /** "fr-FR", "pt_BR", "ZH" → "fr", "pt", "zh". */
    private static String language(String locale) {
        if (locale == null || locale.isBlank()) {
            return "en";
        }
        return locale.trim().split("[-_]")[0].toLowerCase(Locale.ROOT);
    }
}
