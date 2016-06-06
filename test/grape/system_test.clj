(ns grape.system-test
  (:require [grape.store :as store]
            [schema.core :as s]
            [grape.hooks.core :refer [hooks]]
            [schema.spec.core :as spec]
            [schema.spec.leaf :as leaf]
            [grape.schema :refer [read-only Url Str resource-exists ?]]
            [monger.core :as mg]
            [monger.collection :as mc]
            [grape.core :refer [read-item]])
  (:import (org.bson.types ObjectId)
           (org.joda.time DateTime)))

(def db (mg/get-db (mg/connect) "test"))

(def fixtures {"companies"   [{:_id (ObjectId. "caccccccccccccccccccccc1") :name "Pied Piper" :domain "www.piedpiper.com"}
                              {:_id (ObjectId. "caccccccccccccccccccccc2") :name "Raviga" :domain "c2.com"}]
               "permissions" [{:_id (ObjectId. "eeeeeeeeeeeeeeeeeeeeeee1") :company (ObjectId. "caccccccccccccccccccccc1") :user (ObjectId. "aaaaaaaaaaaaaaaaaaaaaaa1") :roles ["ADMIN"]}
                              {:_id (ObjectId. "eeeeeeeeeeeeeeeeeeeeeee2") :company (ObjectId. "caccccccccccccccccccccc1") :user (ObjectId. "aaaaaaaaaaaaaaaaaaaaaaa2") :roles ["USER"]}
                              {:_id (ObjectId. "eeeeeeeeeeeeeeeeeeeeeee3") :company (ObjectId. "caccccccccccccccccccccc2") :user (ObjectId. "aaaaaaaaaaaaaaaaaaaaaaa3") :roles ["ADMIN"]}]
               "users"       [{:_id (ObjectId. "aaaaaaaaaaaaaaaaaaaaaaa1") :company (ObjectId. "caccccccccccccccccccccc1") :username "user 1" :email "user1@c1.com" :password "secret"}
                              {:_id (ObjectId. "aaaaaaaaaaaaaaaaaaaaaaa2") :company (ObjectId. "caccccccccccccccccccccc1") :username "user 2" :email "user2@c1.com" :password "secret"}
                              {:_id (ObjectId. "aaaaaaaaaaaaaaaaaaaaaaa3") :company (ObjectId. "caccccccccccccccccccccc2") :username "user 3" :email "user3@c2.com"}]
               "comments"    [{:_id (ObjectId. "ccccccccccccccccccccccc1") :user (ObjectId. "aaaaaaaaaaaaaaaaaaaaaaa1") :company (ObjectId. "caccccccccccccccccccccc1") :text "this company is cool" :extra "extra field"}
                              {:_id (ObjectId. "ccccccccccccccccccccccc2") :user (ObjectId. "aaaaaaaaaaaaaaaaaaaaaaa2") :company (ObjectId. "caccccccccccccccccccccc1") :text "love you guys :D"}
                              {:_id (ObjectId. "ccccccccccccccccccccccc3") :user (ObjectId. "aaaaaaaaaaaaaaaaaaaaaaa2") :company (ObjectId. "caccccccccccccccccccccc1") :text "spam"}
                              {:_id (ObjectId. "ccccccccccccccccccccccc4") :user (ObjectId. "aaaaaaaaaaaaaaaaaaaaaaa2") :company (ObjectId. "caccccccccccccccccccccc1") :text "has been deleted" :_deleted true}]})

(def store-inst
  (store/map->MongoDataSource {:db db}))

(def CompaniesResource
  {:datasource        {:source "companies"}
   :schema            {(? :_id)      ObjectId
                       :name         s/Str
                       (? :domain)   s/Str
                       (? :roles)    (read-only [s/Str])
                       (? :info)     (s/maybe {:gender (s/enum :male :female)})
                       (? :pages)    [{:url         Url
                                       :description (Str 5 80)}]
                       (? :features) (s/maybe {:premium s/Bool})
                       }
   :url               "companies"
   :operations        #{:create :read :update :delete}
   :public-operations #{:create}
   :auth-strategy     {:type       :field
                       :auth-field :auth_id
                       :doc-field  :_id}
   :soft-delete       true
   :relations         {:permissions {:type     :ref-field
                                     :arity    :many
                                     :path     [:permissions]
                                     :resource :companies-permissions
                                     :field    :company}
                       :users       {:type :ref-field}}})

(def UsersResource
  {:datasource        {:source "users"}
   :schema            {(? :_id)  ObjectId
                       :company  (s/constrained ObjectId (resource-exists :companies) "resource-exists")
                       :username #"^[A-Za-z0-9_ ]{2,25}$"
                       :email    s/Str
                       :password s/Str}
   :fields            #{:_id :company :username :email}
   :url               "users"
   :operations        #{:create :read :update :delete}
   :public-operations #{:create}
   :auth-strategy     {:type       :field
                       :auth-field :auth_id
                       :doc-field  :_id}
   :relations         {:permissions {:type     :ref-field
                                     :arity    :many
                                     :path     [:permissions]
                                     :resource :users-permissions
                                     :field    :user}
                       :comments    {:type     :ref-field
                                     :arity    :many
                                     :path     [:comments]
                                     :resource :comments
                                     :field    :user}}
   :extra-endpoints   [["me" (fn [deps resource request]
                               (read-item deps resource request {}))]]})

(def PublicUsersResource
  {:datasource {:source "users"}
   :schema     {}
   :fields     #{:_id :username}
   :url        "public_users"
   :operations #{:read}})

(def CompaniesPermissionsResource
  {:datasource    {:source "permissions"}
   :schema        {:_id     ObjectId
                   :company ObjectId
                   :user    ObjectId
                   :roles   [s/Str]}
   :url           "companies_permissions"
   :operations    #{:create :read :update :delete}
   :auth-strategy {:type       :field
                   :auth-field :auth_id
                   :doc-field  :account}})

(def UsersPermissionsResource
  (merge CompaniesPermissionsResource
         {:url           "users_permissions"
          :operations    #{:read :delete}
          :auth-strategy {:type       :field
                          :auth-field :auth_id
                          :doc-field  :user}}))

(def CommentsResource
  {:datasource        {:source "comments"}
   :schema            {(? :_id)      ObjectId
                       :user         ObjectId
                       :company      ObjectId
                       :text         s/Str
                       (? :_created) (read-only DateTime)
                       (? :_updated) (read-only DateTime)}
   :url               "comments"
   :operations        #{:create :read :update :delete}
   :public-operations #{:read}
   :auth-strategy     {:type       :field
                       :auth-field :auth_id
                       :doc-field  :user}
   :relations         {:user {:type     :embedded
                              :path     [:user]
                              :resource :public-users}}
   :soft-delete       true})

(def config {:default-paginate {:per-page 10}
             :default-sort     {:sort {:_created -1}}})

(def deps {:store              store-inst
           :resources-registry {:companies             CompaniesResource
                                :users                 UsersResource
                                :public-users          PublicUsersResource
                                :companies-permissions CompaniesPermissionsResource
                                :users-permissions     UsersPermissionsResource
                                :comments              CommentsResource}
           :hooks              hooks
           :config             config})

(defn load-fixtures []
  (doseq [[coll docs] fixtures]
    (mc/drop db coll)
    (doseq [doc docs]
      (mc/insert db coll doc))))